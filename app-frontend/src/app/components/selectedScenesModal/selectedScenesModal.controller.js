export default class SelectedScenesModalController {
    constructor($log, $state) {
        'ngInject';
        this.$state = $state;
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
}
