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
                sceneService.query({id: this.sceneId}).then(function (scene) {
                    this.scene = scene;
                    this.loading = false;
                }.bind(this), function (error) {
                    this.error = error;
                    this.$log.log(error);
                    this.$state.go('^.list');
                }.bind(this));
            } else {
                this.$state.go('^.list');
            }
        }
    }
}
export default SceneDetailController;
