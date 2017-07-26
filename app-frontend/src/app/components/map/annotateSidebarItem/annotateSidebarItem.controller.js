/* globals L, $*/

export default class AnnotateSidebarItemController {
    constructor(
        $log, $scope, $location, $anchorScroll
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$location = $location;
        this.$anchorScroll = $anchorScroll;
    }

    $onInit() {
    }

    onAnnotationClone(annotation) {
        this.onCloneAnnotation({
            'geometry': annotation.geometry,
            'label': annotation.properties.label,
            'description': annotation.properties.description
        });
    }

    /* eslint-disable no-underscore-dangle */
    onAnnotationEdit(annotation) {
        this.onUpdateAnnotationStart({'annotation': annotation});
    }
    /* eslint-enable no-underscore-dangle */

    onAnnotationDelete(annotation) {
        this.onDeleteAnnotation({
            'id': annotation.properties.id,
            'label': annotation.properties.label
        });
    }

    cancelAnnotation(annotation) {
        this.onCancelUpdateAnnotation({
            'annotation': annotation,
            'isEdit': this.editId === annotation.properties.id
        });
    }

    updateAnnotation(annotation) {
        if (this.labelObj) {
            this.isInvalid = false;
            this.newLabelName
                = this.labelObj.originalObject.name || this.labelObj.originalObject;
        } else if (annotation.properties.label) {
            this.isInvalid = false;
            this.newLabelName = annotation.properties.label;
        } else {
            this.isInvalid = true;
        }

        if (this.newLabelName) {
            this.onUpdateAnnotationFinish({
                'id': annotation.properties.id,
                'label': this.newLabelName,
                'description': this.newLabelDescription,
                'isEdit': this.editId === annotation.properties.id
            });
        }
    }
}
