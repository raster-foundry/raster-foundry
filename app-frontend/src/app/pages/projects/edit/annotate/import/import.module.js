/* globals _, $ */
import angular from 'angular';

require('./import.scss');

class AnnotateImportController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $timeout
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$parent = $scope.$parent.$ctrl;
    }

    $onInit() {
        this.matchKeys = {};
        this.bindUploadEvent();
        this.isMachineData = false;
        this.$scope.$on('$destroy', this.$onDestroy.bind(this));
    }

    $onDestroy() {
        this.matchKeys = {};
        this.isMachineData = false;
    }

    /* eslint-disable no-undef */
    bindUploadEvent() {
        $('#btn-upload').change((e) => {
            let upload = _.values(e.target.files);
            if (upload.length) {
                upload.forEach((datum) => {
                    let reader = new FileReader();
                    reader.onload = (event) => {
                        this.setSelectionMenuItems(JSON.parse(event.target.result));
                    };
                    reader.readAsText(datum);
                });
            }
        });
    }
    /* eslint-enable no-undef */

    isMachineMade() {
        this.isMachineData = !this.isMachineData;
        if (!this.isMachineData) {
            this.matchKeys = _.pick(this.matchKeys, ['label', 'description']);
            if (_.keys(this.matchKeys).length !== 2) {
                this.enableImport = false;
            } else {
                this.enableImport = true;
            }
        } else if (this.isMachineData && _.keys(this.matchKeys).length !== 4) {
            this.enableImport = false;
        }
    }

    setSelectionMenuItems(data) {
        this.uploadData = data;
        this.dataProperties = data.features.reduce((accu, f) => {
            return _.intersection(accu, Object.keys(f.properties));
        }, Object.keys(data.features[0].properties));
        this.$scope.$apply();
    }

    updateKeySelection(appKey, dataKey) {
        this.matchKeys[appKey] = dataKey;
        if (this.isMachineData && _.keys(this.matchKeys).length === 4) {
            this.enableImport = true;
        } else if (!this.isMachineData && _.keys(this.matchKeys).length === 2) {
            this.enableImport = true;
        }
    }

    onImportClick() {
        this.$parent.importLocalAnnotations({
            'type': 'FeatureCollection',
            'features': this.uploadData.features.map((f) => {
                let con = this.isMachineData ? f.properties[this.matchKeys.confidence] : null;
                let qa = this.isMachineData ? f.properties[this.matchKeys.qualityCheck] : null;
                return {
                    'properties': {
                        'label': f.properties[this.matchKeys.label],
                        'description': f.properties[this.matchKeys.description],
                        'machineGenerated': this.isMachineData,
                        'confidence': con,
                        'qualityCheck': qa
                    },
                    'geometry': f.geometry,
                    'type': 'Feature'
                };
            })
        });
        this.$state.go('projects.edit.annotate');
    }
}

const AnnotateImportModule = angular.module('pages.projects.edit.annotate.import', []);

AnnotateImportModule.controller(
    'AnnotateImportController', AnnotateImportController
);

export default AnnotateImportModule;
