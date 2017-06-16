// Component code
import projectItemTpl from './projectItem.html';

const rfProjectItem = {
    templateUrl: projectItemTpl,
    controller: 'ProjectItemController',
    bindings: {
        project: '<',
        selected: '&',
        onSelect: '&'
    }
};

export default rfProjectItem;
