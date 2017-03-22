export default class SelectedScenesModalController {
    constructor($log, $state, projectService) {
        'ngInject';
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

    viewSceneDetail(scene) {
        this.$state.go('browse', {id: scene.id});
        this.dismiss();
    }

    addScenesToProject() {
        let sceneIds = Array.from(this.selectedScenes.keys());
        this.projectService.addScenes(this.resolve.project.id, sceneIds).then(
            () => {
                this.resolve.scenes.clear();
                this.close();
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
