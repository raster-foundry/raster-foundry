/* globals describe beforeEach it expect inject */
/* eslint max-nested-callbacks: 0 */
require('./leafletMap.module');

describe('component: rfLeafletMap', () => {
    let compile;
    let scope;
    let componentElem;

    function getCompiledElement() {
        let element = angular.element('<div id="map"></div>');
        let compiledElement = compile(element)(scope);
        scope.$digest();
        return compiledElement;
    }

    beforeEach(() => {
        inject(function ($compile, $rootScope) {
            compile = $compile;
            scope = $rootScope.$new();
        });

        componentElem = getCompiledElement();
    });


    it('should contain a map element', () => {
        let mapElem = componentElem.find('#map');
        expect(mapElem).toBeDefined();
    });

    it('should initialize a leaflet map', () => {
        let mapElem = componentElem.find('.leaflet-map-pane');
        expect(mapElem).toBeDefined();
    });
});
