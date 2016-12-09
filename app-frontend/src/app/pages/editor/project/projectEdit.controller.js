const Map = require('es6-map');

export default class ProjectEditController {
    constructor( // eslint-disable-line max-params
    ) {
        this.selectedScenes = new Map();
        this.selectedLayers = new Map();
        this.sceneList = [];
        this.sceneLayers = new Map();
        this.layers = [];
    }
}
