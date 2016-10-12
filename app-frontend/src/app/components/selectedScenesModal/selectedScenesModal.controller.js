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

    setSelected(scene, selected) {
        if (selected) {
            this.selectedScenes.set(scene.id, scene);
        } else {
            this.selectedScenes.delete(scene.id);
        }
    }

    viewSceneDetail(scene) {
        this.$state.go('browse', {id: scene.id});
        this.dismiss();
    }
}
