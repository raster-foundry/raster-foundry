// Component code
// https://docs.angularjs.org/guide/component

import leafletMapTemplate from './leafletMap.html';

const leafletMap = {
    template: leafletMapTemplate,
    controller: 'LeafletMapController',
    bindings: {
        rfMapId: '@'
    }
};

export default leafletMap;
