/* globals _*/
import angular from 'angular';

class AnnotateExportController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
    }

    $onInit() {
        this.filteredAnnotations = this.$parent.filteredAnnotations;
        this.annoToExport = this.$parent.annoToExport;
    }

    onAnnotationsDownload(e, annotationData) {
        let href = `data:text/json;charset=utf-8,${encodeURI(JSON.stringify(annotationData))}`;
        let dl = angular.element(`<a href="${href}"
            download="${this.fileName}.geojson"></a>`);
        angular.element(e.target).parent().append(dl);
        dl[0].click();
        dl.remove();
    }

    createExportData(data, omitKeys) {
        let propertyKeys = _.difference(
            _.keys(data.features[0].properties),
            omitKeys
        );
        return {
            features: data.features.map((f) => {
                return {
                    geometry: f.geometry,
                    properties: _.pick(f.properties, propertyKeys),
                    type: 'Feature'
                };
            }),
            type: 'FeatureCollection'
        };
    }

    onExportClick(e) {
        if (this.filteredAnnotations.features && this.filteredAnnotations.features.length) {
            this.onAnnotationsDownload(
                e,
                this.createExportData(this.filteredAnnotations, ['id', 'prevId'])
            );
        } else if (this.annoToExport.features && this.annoToExport.features.length) {
            this.onAnnotationsDownload(
                e,
                this.createExportData(this.annoToExport, ['id', 'prevId'])
            );
        } else {
            this.onAnnotationsDownload(e, {'result': 'Nothing to export.'});
        }

        this.$parent.onFilterChange({'name': 'All'});
        this.$state.go('projects.edit.annotate');
    }
}

const AnnotateExportModule = angular.module('pages.projects.edit.annotate.export', []);

AnnotateExportModule.controller(
    'AnnotateExportController', AnnotateExportController
);

export default AnnotateExportModule;
