// Component code
import projectItemTpl from './projectItem.html';

const rfProjectItem = {
    templateUrl: projectItemTpl,
    controller: 'ProjectItemController',
    transclude: true,
    bindings: {
        project: '<',
        selected: '&',
        onSelect: '&',
        slim: '<',
        hideOptions: '<'
    }
};

export default rfProjectItem;
