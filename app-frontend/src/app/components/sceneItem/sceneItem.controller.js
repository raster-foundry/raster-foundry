export default class SceneItemController {
    constructor() {
        'ngInject';
    }

    toggleSelected(event) {
        this.selected = !this.selected;
        event.stopPropagation();
    }
}
