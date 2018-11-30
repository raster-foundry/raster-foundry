/* globals _, $, FileReader */
import angular from 'angular';

require('./import.scss');

class AnnotateImportController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $timeout, $ngRedux
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$parent = $scope.$parent.$ctrl;

        $ngRedux.subscribe(() => {
            this.state = $ngRedux.getState();
            this.uploadAnnotationsError = this.state.projects.uploadAnnotationsError;
        });
    }

    $onInit() {
        this.matchKeys = {};
        this.defaultKeys = {
            label: '',
            description: '',
            quality: null
        };
        this.bindGeoJSONUploadEvent();
        this.bindShapefileUploadEvent();
        this.isMachineData = false;

        this.$scope.$watch('$ctrl.$parent.annotationShapefileProps', (props) => {
            if (props && props.length) {
                this.hasShapefileProps = true;
                this.dataProperties = props;
            }
        });
    }

    bindGeoJSONUploadEvent() {
        $('#geojson-btn-upload').change((e) => {
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

    bindShapefileUploadEvent() {
        $('#shapefile-btn-upload').change((e) => {
            let upload = _.values(e.target.files);
            if (upload.length) {
                upload.forEach((datum) => {
                    this.setShapefileUploadData(datum);
                });
            }
        });
    }

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
        this.dataProperties = data.features.reduce((accu, feature) => {
            return _.intersection(accu, Object.keys(feature.properties));
        }, Object.keys(data.features[0].properties));
        this.$scope.$apply();
    }

    setShapefileUploadData(shapefileData) {
        this.shapefileData = shapefileData;
        this.$parent.uploadShapefile(shapefileData);
    }

    updateKeySelection(appKey, dataKey) {
        this.matchKeys[appKey] = dataKey;
        if (this.isMachineData && _.keys(this.matchKeys).length === 4) {
            this.enableImport = true;
        } else if (!this.isMachineData && _.keys(this.matchKeys).length === 2) {
            this.enableImport = true;
        }
    }

    hasDefaultVal(appKey) {
        return typeof this.defaultKeys[appKey] !== 'undefined';
    }

    defaultKeySelection(appKey, defaultVal) {
        this.defaultKeys[appKey] = defaultVal;
    }

    getValOrDefault(appKey, feature) {
        if (this.matchKeys[appKey]) {
            return feature.properties[this.matchKeys[appKey]];
        }
        return this.defaultKeys[appKey];
    }

    onImportClick() {
        if (this.uploadData) {
            this.$parent.createAnnotations({
                'type': 'FeatureCollection',
                'features': this.uploadData.features.map((feature) => {
                    let confidence = null;
                    let quality = null;
                    if (this.isMachineData) {
                        confidence = this.matchKeys.confidence ?
                            feature.properties[this.matchKeys.confidence] : null;
                        quality = this.getValOrDefault('quality', feature);
                    }
                    return {
                        'properties': {
                            'label': this.getValOrDefault('label', feature).toString(),
                            'description': (
                                this.getValOrDefault('description', feature) || ''
                            ).toString(),
                            'machineGenerated': this.isMachineData,
                            'confidence': confidence,
                            'quality': quality
                        },
                        'geometry': feature.geometry,
                        'type': 'Feature'
                    };
                })
            });
        }

        if (this.hasShapefileProps) {
            this.$parent.importShapefileWithProps(this.shapefileData, this.matchKeys);
            this.hasShapefileProps = false;
        }

        this.$state.go('projects.edit.annotate');
    }

    onGoToParent() {
        this.hasShapefileProps = false;
        this.dataProperties = [];
        this.$parent.deleteShapeFileUpload();
        this.$state.go('projects.edit.annotate');
    }
}

const AnnotateImportModule = angular.module('pages.projects.edit.annotate.import', []);

AnnotateImportModule.controller(
    'AnnotateImportController', AnnotateImportController
);

export default AnnotateImportModule;
