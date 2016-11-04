// Component code
// https://docs.angularjs.org/guide/component

const leafletMap = {
    template: '<div></div>',
    controller: 'LeafletMapController',
    bindings: {
        static: '<?',
        footprint: '<?',
        proposedBounds: '<?',
        onBoundsChange: '&'
    }
};

export default leafletMap;
