/* globals L, _, $*/

export default class AnnotateSidebarItemController {
    constructor(
        $log, $scope,
        mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;

        this.getMap = () => mapService.getMap(this.mapId);
    }

    $onInit() {
        L.drawLocal.edit.handlers.edit.tooltip.subtext = '';
    }

    addAnnotation(annotation) {
        if (this.labelObj) {
            $('#_value').css('border-color', '#fff');

            this.newLabelName = this.labelObj.originalObject.name || this.labelObj.originalObject;

            this.onAddAnnotation({
                'id': annotation.properties.id,
                'label': this.newLabelName,
                'description': this.newLabelDescription
            });
        } else {
            $('#_value').css('border-color', '#da7976');
        }
    }

    cancelAnnotation(annotation) {
        if (this.isEditing) {
            this.editHandler.disable();
            this.isEditing = false;
            this.onCancelUpdateAnnotation({'id': annotation.properties.id});
        } else {
            this.onCancelAddAnnotation({'id': annotation.properties.id});
        }
    }

    /* eslint-disable no-underscore-dangle */
    onAnnotationEdit(annotation) {
        this.isEditing = true;

        this.getMap().then((mapWrapper) => {
            let annotationLayers = mapWrapper.getLayers('Annotation');
            let annotationLayerToEdit = _.filter(annotationLayers, (l) => {
                return _.first(_.values(l._layers))
                    .feature.properties.id === annotation.properties.id;
            });

            let otherAnnotationLayers = _.difference(annotationLayers, annotationLayerToEdit);

            mapWrapper.setLayer('Annotation', otherAnnotationLayers, true);
            mapWrapper.setLayer('draw', annotationLayerToEdit, false);

            this.setEditHandler(mapWrapper, annotationLayerToEdit[0]);
        });

        let md = _.filter(this.data.features, (f) => f.properties.id === annotation.properties.id);
        this.newLabelName = md[0].properties.label;
        this.newLabelDescription = md[0].properties.description;

        this.initialLabelName = annotation.properties.label;

        this.onUpdateAnnotationStart();
    }
    /* eslint-enable no-underscore-dangle */

    setEditHandler(mapWrapper, editLayer) {
        this.editHandler = new L.EditToolbar.Edit(mapWrapper.map, {
            featureGroup: editLayer
        });
        this.editHandler.enable();
    }

    onAnnotationDelete(annotation) {
        this.onDeleteAnnotation({
            'id': annotation.properties.id,
            'label': annotation.properties.label
        });
    }

    updateAnnotation(annotation) {
        this.getMap().then((mapWrapper) => {
            if (this.labelObj) {
                $('#_value').css('border-color', '#fff');

                this.editHandler.save();
                this.editHandler.disable();

                this.newLabelName
                    = this.labelObj.originalObject.name || this.labelObj.originalObject;

                this.onUpdateAnnotationFinish({
                    'layer': mapWrapper.getLayers('draw')[0],
                    'id': annotation.properties.id,
                    'label': this.newLabelName,
                    'oldLabel': annotation.properties.label,
                    'description': this.newLabelDescription
                });

                this.isEditing = false;
            } else {
                $('#_value').css('border-color', '#da7976');
            }
        });
    }
}
