import angular from 'angular';

import {annotationsToFeatureCollection} from '_redux/annotation-utils';

class AnnotateExportController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $ngRedux
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis
        )(this);
        $scope.$on('$destroy', unsubscribe);
    }

    mapStateToThis(state) {
        return {
            annotations: state.projects.annotations
        };
    }

    $onInit() {
        this.visibleAnnotations = this.$parent.visibleAnnotations;
    }

    onAnnotationsDownload(e, annotationData) {
        let href = `data:text/json;charset=utf-8,${encodeURI(JSON.stringify(annotationData))}`;
        let dl = angular.element(`<a href="${href}"
            download="${this.fileName}.geojson"></a>`);
        angular.element(e.target).parent().append(dl);
        dl[0].click();
        dl.remove();
    }

    onExportClick(e) {
        if (this.visibleAnnotations && this.visibleAnnotations.length) {
            this.onAnnotationsDownload(
                e,
                annotationsToFeatureCollection(this.visibleAnnotations)
            );
        } else if (this.annotations && this.annotations.size) {
            this.onAnnotationsDownload(
                e,
                annotationsToFeatureCollection(this.annotations)
            );
        } else {
            this.onAnnotationsDownload(e, {'result': 'Nothing to export.'});
        }

        this.$state.go('projects.edit.annotate');
    }
}

const AnnotateExportModule = angular.module('pages.projects.edit.annotate.export', []);

AnnotateExportModule.controller(
    'AnnotateExportController', AnnotateExportController
);

export default AnnotateExportModule;
