import angular from 'angular';
import annotateSidebarItemTpl from './annotateSidebarItem.html';
require('./annotateSidebarItem.scss');

const AnnotateSidebarItemComponent = {
    templateUrl: annotateSidebarItemTpl,
    controller: 'AnnotateSidebarItemController',
    bindings: {
        annotation: '<',
        labelInputs: '<',
        editId: '<',
        disableSidebarAction: '<',
        onCloneAnnotation: '&',
        onUpdateAnnotationStart: '&',
        onDeleteAnnotation: '&',
        onUpdateAnnotationFinish: '&',
        onCancelUpdateAnnotation: '&',
        onBulkCreate: '&',
        onQaChecked: '&'
    }
};

class AnnotateSidebarItemController {
    constructor(
        $log, $scope, $timeout
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$timeout = $timeout;
    }

    $onInit() {
        this.minMatchedLabelLength = 3;
        this.maxMatchedLabels = 4;
    }

    onAnnotationClone($event, annotation) {
        $event.stopPropagation();
        this.onCloneAnnotation({
            'geometry': annotation.geometry,
            'label': annotation.properties.label,
            'description': annotation.properties.description
        });
    }

    onAnnotationEdit($event, annotation) {
        $event.stopPropagation();
        this.onUpdateAnnotationStart({'annotation': annotation});
    }

    onAnnotationDelete($event, annotation) {
        $event.stopPropagation();
        this.onDeleteAnnotation({
            'id': annotation.properties.id,
            'label': annotation.properties.label
        });
    }

    onAnnotationBulkCreate($event, annotation) {
        $event.stopPropagation();
        this.onBulkCreate({'annotation': annotation});
    }

    cancelAnnotation(annotation) {
        this.onCancelUpdateAnnotation({
            'annotation': annotation,
            'isEdit': this.editId === annotation.properties.id
        });
    }

    updateAnnotation(annotation) {
        if (this.labelNameInput) {
            this.isInvalid = false;
            this.newLabelName = this.labelNameInput;
        } else {
            this.isInvalid = true;
        }

        this.showMatchedLabels = false;

        if (this.newLabelName) {
            this.onUpdateAnnotationFinish({
                'id': annotation.properties.id,
                'label': this.newLabelName,
                'description': this.newLabelDescription,
                'isEdit': this.editId === annotation.properties.id
            });
        }
    }

    onQaCheck(annotation, qa) {
        this.onQaChecked({annotation, qa});
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
        this.labelInputsMatch = this.labelInputs.filter((label) => {
            return label.name.toString().toUpperCase().includes(normalizedLabel);
        });
        if (this.labelInputsMatch.length) {
            this.showMatchedLabels = true;
            this.labelInputsMatch.sort((a, b) => a.name.length - b.name.length);
            if (this.labelInputsMatch.length >= this.maxMatchedLabels) {
                this.labelInputsMatch = this.labelInputsMatch.slice(0, this.maxMatchedLabels);
            }
        }
    }

    onSelectLabelName(labelName) {
        this.labelNameInput = labelName.name;
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
