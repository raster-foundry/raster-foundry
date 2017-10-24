/* globals L, $*/

export default class AnnotateSidebarItemController {
    constructor(
        $log, $scope
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
    }

    $onInit() {
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
        if (this.labelNameInput.length >= 3) {
            this.matchLabelName(this.labelNameInput);
        } else {
            this.showMatchedLabels = false;
        }
        this.isInvalid = false;
    }

    matchLabelName(labelName) {
        this.labelInputsMatch = this.labelInputs.filter((label) => {
            return label.name.toString().toUpperCase().includes(labelName.toString().toUpperCase());
        });
        if (this.labelInputsMatch.length) {
            this.showMatchedLabels = true;
            this.labelInputsMatch.sort((a, b) => a.name.length - b.name.length);
            if (this.labelInputsMatch.length >= 4) {
                this.labelInputsMatch = this.labelInputsMatch.slice(0, 4);
            }
        }
    }

    onSelectLabelName(labelName) {
        this.labelNameInput = labelName.name;
        this.showMatchedLabels = false;
    }

    onTextFieldClick() {
        this.showMatchedLabels = false;
    }
}
