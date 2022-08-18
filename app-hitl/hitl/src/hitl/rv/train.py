from rastervision.core.data import (ClassConfig, DatasetConfig, Scene)
from rastervision.pytorch_learner import (
    SemanticSegmentationGeoDataConfig, SemanticSegmentationLearner,
    SemanticSegmentationLearnerConfig, SemanticSegmentationModelConfig,
    SolverConfig, SemanticSegmentationRandomWindowGeoDataset,
    ExternalModuleConfig)


def train(scene: Scene, class_config: ClassConfig, output_dir: str,
          **kwargs) -> SemanticSegmentationLearner:
    chip_sz = kwargs.get('chip_sz', 256)
    img_sz = kwargs.get('img_sz', 256)
    num_classes = len(class_config)
    img_channels = len(scene.raster_source.channel_order)
    init_weights = kwargs.get('init_weights', None)

    data_cfg = SemanticSegmentationGeoDataConfig(
        num_workers=kwargs.get('num_workers', 4),
        img_channels=img_channels,
        img_sz=img_sz,
        scene_dataset=DatasetConfig(
            class_config=class_config, train_scenes=[], validation_scenes=[]),
        window_opts={})

    if kwargs.get('external_model'):
        entrypoint_kwargs = dict(
            name='resnet18',
            fpn_type='panoptic',
            num_classes=num_classes,
            fpn_channels=128,
            in_channels=img_channels,
            out_size=(img_sz, img_sz),
            pretrained=(init_weights is None))
        entrypoint_kwargs.update(kwargs.get('external_model_kwargs', {}))
        model_cfg = SemanticSegmentationModelConfig(
            external_def=ExternalModuleConfig(
                github_repo='AdeelH/pytorch-fpn:0.3',
                name='fpn',
                entrypoint='make_fpn_resnet',
                entrypoint_kwargs=entrypoint_kwargs),
            init_weights=init_weights)
    else:
        model_cfg = SemanticSegmentationModelConfig(
            pretrained=(init_weights is None), init_weights=init_weights)

    learner_cfg = SemanticSegmentationLearnerConfig(
        output_uri=output_dir,
        data=data_cfg,
        model=model_cfg,
        solver=SolverConfig(
            batch_sz=kwargs.get('batch_sz', 16),
            num_epochs=kwargs.get('num_epochs', 1),
            lr=kwargs.get('lr', 3e-4)))

    train_ds = SemanticSegmentationRandomWindowGeoDataset(
        scene,
        out_size=img_sz,
        size_lims=(chip_sz, chip_sz + 1),
        max_windows=kwargs.get('num_chips', 100))

    val_ds = SemanticSegmentationRandomWindowGeoDataset(
        scene, out_size=img_sz, size_lims=(img_sz, img_sz + 1), max_windows=10)

    learner = SemanticSegmentationLearner(
        cfg=learner_cfg, train_ds=train_ds, valid_ds=val_ds, test_ds=val_ds)

    learner.train()

    return learner
