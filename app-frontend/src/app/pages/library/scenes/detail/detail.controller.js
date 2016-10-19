class SceneDetailController {
    constructor($log, $state, sceneService) {
        'ngInject';

        this.$state = $state;
        this.$log = $log;

        this.scene = this.$state.params.scene;
        this.sceneId = this.$state.params.id;
        if (!this.scene) {
            if (this.sceneId) {
                this.loading = true;
                sceneService.query({id: this.sceneId}).then(
                    (scene) => {
                        this.scene = scene;
                        this.loading = false;
                    },
                    () => {
                        this.$state.go('^.list');
                    }
                );
            } else {
                this.$state.go('^.list');
            }
        }
    }
}
export default SceneDetailController;
