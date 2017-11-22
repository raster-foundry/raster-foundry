import angular from 'angular';
import annotateSidebarItemTpl from './annotateSidebarItem.html';
require('./annotateSidebarItem.scss');

import AnnotationActions from '_redux/actions/annotation-actions';
import {wrapFeatureCollection} from '_redux/annotation-utils';

const AnnotateSidebarItemComponent = {
    templateUrl: annotateSidebarItemTpl,
    controller: 'AnnotateSidebarItemController',
    bindings: {
        annotationId: '<',
        onBulkCreate: '&'
    }
};

class AnnotateSidebarItemController {
    constructor(
        $log, $scope, $timeout, $ngRedux, $window
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$window = $window;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            AnnotationActions
        )(this);
        $scope.$on('$destroy', unsubscribe);
    }

    mapStateToThis(state) {
        let annotation;
        if (this.annotationId) {
            annotation = state.projects.annotations.get(this.annotationId);
        }

        return {
            annotation,
            editingAnnotation: state.projects.editingAnnotation,
            sidebarDisabled: state.projects.sidebarDisabled,
            labels: state.projects.labels
        };
    }

    $onInit() {
        this.minMatchedLabelLength = 3;
        this.maxMatchedLabels = 4;
    }

    onAnnotationClone($event) {
        $event.stopPropagation();
        this.createAnnotations(wrapFeatureCollection(this.annotation), true);
    }

    onAnnotationEdit($event) {
        $event.stopPropagation();
        this.editAnnotation(this.annotationId);
    }

    onAnnotationDelete($event) {
        $event.stopPropagation();
        let answer = this.$window.confirm('Delete this annotation?');
        if (answer) {
            this.deleteAnnotation(this.annotationId);
        }
    }

    onBulkCreateClick($event) {
        $event.stopPropagation();
        this.bulkCreateAnnotations(this.annotation);
    }

    onSaveClick() {
        if (this.labelNameInput) {
            this.isInvalid = false;
            this.newLabelName = this.labelNameInput;
        } else {
            this.isInvalid = true;
        }

        this.showMatchedLabels = false;

        let annotation = Object.assign({}, this.annotation, {
            properties: Object.assign({}, this.annotation.properties, {
                label: this.newLabelName,
                description: this.newLabelDescription
            })
        });
        this.finishEditingAnnotation(annotation);
    }

    onQaCheck(qa) {
        this.updateAnnotation(Object.assign({}, this.annotation, {
            properties: Object.assign({}, this.annotation.properties, {
                quality: qa
            })
        }));
    }

    onLabelNameChange() {
        this.showMatchedLabels = false;
        this.isInvalid = false;
        if (this.labelNameInput.length >= this.minMatchedLabelLength) {
            this.matchLabelName(this.labelNameInput);
        }
    }

    matchLabelName(labelName) {
        let normalizedLabel = labelName.toString().toUpperCase();
        this.labelInputsMatch = this.labels.filter((label) => {
            return label.toUpperCase().includes(normalizedLabel);
        });
        if (this.labelInputsMatch.length) {
            this.showMatchedLabels = true;
            this.labelInputsMatch.sort((a, b) => a.length - b.length);
            if (this.labelInputsMatch.length >= this.maxMatchedLabels) {
                this.labelInputsMatch = this.labelInputsMatch.slice(0, this.maxMatchedLabels);
            }
        }
    }

    onSelectLabelName(labelName) {
        this.labelNameInput = labelName;
        this.showMatchedLabels = false;
        this.isMouseOnLabelOption = false;
    }

    onLabelFieldBlur() {
        if (!this.isMouseOnLabelOption) {
            this.showMatchedLabels = false;
        }
    }

    onLabelFieldFocus() {
        if (this.labelNameInput.length >= this.minMatchedLabelLength) {
            this.matchLabelName(this.labelNameInput);
        }
    }

    onHoverOption(isMouseHovered) {
        this.isMouseOnLabelOption = isMouseHovered;
    }
}

const AnnotateSidebarItemModule = angular.module('components.map.annotateSidebarItem', []);

AnnotateSidebarItemModule.component('rfAnnotateSidebarItem', AnnotateSidebarItemComponent);
AnnotateSidebarItemModule.controller(
    'AnnotateSidebarItemController',
    AnnotateSidebarItemController
);

export default AnnotateSidebarItemModule;
