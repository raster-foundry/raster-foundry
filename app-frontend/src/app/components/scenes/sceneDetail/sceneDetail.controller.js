export default class SceneDetailComponentController {
    constructor(thumbnailService, datasourceService) {
        'ngInject';
        this.thumbnailService = thumbnailService;
        this.datasourceService = datasourceService;
    }

    $onInit() {
        this.datasourceLoaded = false;
        this.datasourceService.get(this.scene.datasource).then(d => {
            this.datasourceLoaded = true;
            this.datasource = d;
        });
    }
}
