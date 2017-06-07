export default class ProjectAddScenesModalController {
    constructor($log, $state, projectService) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.projectService = projectService;
        this.scenes = [];
        this.selectedScenes = this.resolve.scenes;
        this.selectedScenes.forEach((value) => {
            this.scenes.push(value);
        });
    }

    isSelected(scene) {
        return this.selectedScenes.has(scene.id);
    }

    viewSceneDetail() {
        // open scene preview modal, instead of navving to browse
        this.dismiss();
    }

    addScenesToProject() {
        let sceneIds = Array.from(this.selectedScenes.keys());
        this.projectService.addScenes(this.resolve.project.id, sceneIds).then(
            () => {
                this.close({ $value: sceneIds });
            },
            (err) => {
                // TODO: Show toast or error message instead of debug message
                this.$log.debug(
                    'Error while adding scenes to project',
                    this.resolve.project.id, err
                );
            }
        );
    }
}
