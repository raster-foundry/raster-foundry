from typing import Optional, Tuple
from os.path import join

from rastervision.core.data import (
    ClassConfig, Scene, SemanticSegmentationLabelStore,
    PolygonVectorOutputConfig, SemanticSegmentationSmoothLabels)
from rastervision.pytorch_learner import (
    SemanticSegmentationLearner, SemanticSegmentationSlidingWindowGeoDataset)


def predict(learner: SemanticSegmentationLearner,
            scene: Scene,
            class_config: ClassConfig,
            output_dir: str,
            chip_sz: int = 256,
            stride: Optional[int] = None
            ) -> Tuple[SemanticSegmentationSmoothLabels, str]:
    if stride is None:
        stride = chip_sz

    vec_pred_uri = join(output_dir, 'pred.geojson')
    label_store = SemanticSegmentationLabelStore(
        uri=output_dir,
        extent=scene.raster_source.get_extent(),
        crs_transformer=scene.raster_source.get_crs_transformer(),
        class_config=class_config,
        smooth_output=True,
        smooth_as_uint8=True,
        vector_outputs=[
            PolygonVectorOutputConfig(uri=vec_pred_uri, class_id=1)
        ])

    base_tf, _ = learner.cfg.data.get_data_transforms()
    ds = SemanticSegmentationSlidingWindowGeoDataset(
        scene, size=chip_sz, stride=stride, transform=base_tf)

    predictions = learner.predict_dataset(
        ds,
        raw_out=True,
        numpy_out=True,
        predict_kw=dict(out_shape=(chip_sz, chip_sz)),
        dataloader_kw=dict(num_workers=0),
        progress_bar=True)

    labels = SemanticSegmentationSmoothLabels.from_predictions(
        ds.windows,
        predictions,
        extent=label_store.extent,
        num_classes=len(class_config))

    label_store.write_vector_outputs(labels)

    return labels, vec_pred_uri
