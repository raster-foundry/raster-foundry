// Component code
import callToActionItemTpl from './callToActionItem.html';

const rfCallToActionItem = {
    templateUrl: callToActionItemTpl,
    controller: 'CallToActionItemController',
    transclude: true,
    bindings: {
        title: '@'
    }
};

export default rfCallToActionItem;
