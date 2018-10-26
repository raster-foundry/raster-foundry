import angular from 'angular';

import {annotationsToFeatureCollection} from '_redux/annotation-utils';

class AnnotateExportController {
    constructor( // eslint-disable-line max-params
        $rootScope, $log, $state, $scope, $ngRedux,
        projectService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.$parent = $scope.$parent.$ctrl;
    }

    mapStateToThis(state) {
        return {
            annotations: state.projects.annotations
        };
    }

    $onInit() {
        let unsubscribe = this.$ngRedux.connect(
            this.mapStateToThis
        )(this);
        this.$scope.$on('$destroy', unsubscribe);

        this.visibleAnnotations = this.$parent.visibleAnnotations;
        this.projectService.getAnnotationShapefile(this.$state.params.projectid).then(
            (res) => {
                this.shapefileDlUri = res.data;
                this.hasShapefile = true;
            },
            (err) => {
                this.hasShapefile = false;
                this.$log.error(err);
            }
        );
    }

    onAnnotationsDownload(e, annotationData) {
        let href = `data:text/json;charset=utf-8,${encodeURI(JSON.stringify(annotationData))}`;
        let dl = angular.element(`<a href="${href}"
            download="${this.fileName}.geojson"></a>`);
        if (this.exportType === 'Shapefile' && this.shapefileDlUri) {
            href = this.shapefileDlUri;
            dl = angular.element(`<a href="${href}"></a>`);
        }
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
