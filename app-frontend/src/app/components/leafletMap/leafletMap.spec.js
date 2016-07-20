import leafletMap from './leafletMap';

describe('component: rfLeafletMap', () => {
    var compile, scope, componentElem;
    beforeEach(() => {
        inject(function($compile, $rootScope) {
            compile = $compile;
            scope = $rootScope.$new();
        });

        componentElem = getCompiledElement();
    });

    function getCompiledElement() {
        var element = angular.element('<div id="map"></div>');
        var compiledElement = compile(element)(scope);
        scope.$digest();
        return compiledElement;
    }

    it('should contain a map element', () => {
        var mapElem = componentElem.find('#map');
        expect(mapElem).toBeDefined();
    });

    it('should initialize a leaflet map', () => {
        var mapElem = componentElem.find('.leaflet-map-pane');
        expect(mapElem).toBeDefined();
    });
});
