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
        slim: '<'
    }
};

export default rfProjectItem;
