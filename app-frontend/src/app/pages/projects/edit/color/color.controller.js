export default class ProjectsEditColorController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, layerService, sceneService, $state, mapService,
        datasourceService
    ) {
        'ngInject';
        this.projectService = projectService;
        this.layerService = layerService;
        this.sceneService = sceneService;
        this.datasourceService = datasourceService;
        this.$state = $state;
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$q = $q;
        this.$log = $log;
        this.getMap = () => mapService.getMap('edit');
    }
}
