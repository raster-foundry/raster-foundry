// Component code
// https://docs.angularjs.org/guide/component

const leafletMap = {
    template: '<div></div>',
    controller: 'LeafletMapController',
    bindings: {
        static: '<?',
        footprint: '<?',
        highlight: '<?',
        bypassFitBounds: '<?',
        grid: '<?',
        proposedBounds: '<?',
        onViewChange: '&',
        onMapClick: '&',
        layers: '<?'
    }
};

export default leafletMap;
