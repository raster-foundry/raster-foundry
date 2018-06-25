import tpl from './editableLogo.html';

class EditableLogoController {
    $onChanges(changes) {
        if (changes.uri) {
            this.processedUri = this.cacheBustUri(changes.uri.currentValue);
        } else if (changes.updateTrigger) {
            this.processedUri = this.cacheBustUri(this.uri);
        }
    }

    cacheBustUri(uri) {
        if (this.noCacheBust) {
            return uri;
        }
        return uri && uri.length ? `${uri}?${new Date().getTime()}` : '';
    }
}

const EditableLogoComponent = {
    bindings: {
        uri: '<',
        canEdit: '<',
        showPlaceholder: '<',
        onEdit: '&',
        updateTrigger: '<',
        noCacheBust: '<'
    },
    controller: 'EditableLogoController',
    templateUrl: tpl
};

export default angular
    .module('components.editableLogo', [])
    .controller('EditableLogoController', EditableLogoController)
    .component('rfEditableLogo', EditableLogoComponent);
