export default class MarketToolController {
    constructor( // eslint-disable-line max-params
        $log, $state, toolService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;

        this.toolService = toolService;
        this.toolData = this.$state.params.toolData;
        this.toolId = this.$state.params.id;
        this.activeSlide = 0;
        this.fetchTool();
        this.populateTestData();
    }

    fetchTool() {
        this.loadingTool = true;
        this.toolService.get(this.toolId).then(d => {
            this.tool = d;
            this.tool.createdAtFormatted = new Date(d.createdAt).toLocaleDateString();
            this.tool.modifiedAtFormatted = new Date(d.modifiedAt).toLocaleDateString();
            this.loadingTools = false;
        });
    }

    populateTestData() {
        this.testScreenshots = [];
        this.similarQueryResult = {
            count: 25,
            hasNext: true,
            hasPrevious: false,
            pageSize: 10,
            page: 1,
            results: [{
                name: 'Normalized Difference Vegetation Index - NDVI',
                description: 'Analyze remote sensing measurements, ' +
                    'and assess whether the target being observed contains' +
                    'live green vegetation or not.',
                uploader: 'Raster Foundry',
                id: 'uuid1',
                screenshots: [],
                tags: [
                    'Image Classification', 'Tagged'
                ],
                categories: [
                    'Climate', 'Ecosystems', 'Forestry', 'Farming', 'Plant Growth',
                    'Prevention'
                ],
                requirements: '1 scene with NIR-1 and red band information',
                createdAt: (new Date()).toISOString(),
                modifiedAt: (new Date()).toISOString()
            }, {
                name: 'Direction of Surface Change',
                description: 'Measures to 2-dimensional rate of surface change.' +
                    'Output will be an integer layer with values 0-360',
                uploader: 'Raster Foundry',
                id: 'uuid2',
                screenshots: [],
                tags: [
                    'Image Classification', 'Tagged'
                ],
                categories: [
                    'Climate', 'Ecosystems', 'Forestry', 'Farming', 'Plant Growth',
                    'Prevention'
                ],
                requirements: '1 scene with NIR-1 and red band information',
                createdAt: (new Date()).toISOString(),
                modifiedAt: (new Date()).toISOString()
            }, {
                name: 'Topographic Position Index (TPI)',
                description: 'The Topographic Position index (TPI) is a relative' +
                    'measure of a location\'s elevation with respect to its ' +
                    'surroundings.  It is calculated by subtracting the mean ' +
                    'elevation of the 8 cells surrounding the cell from the ' +
                    'cell\'s elevation. Given that TPI is calculated by comparing ' +
                    'a central point to the surrounding elevation, there is the ' +
                    'possibility of both positive and negative values.',
                uploader: 'Raster Foundry',
                id: 'uuid3',
                screenshots: [],
                tags: [
                    'Image Classification', 'Tagged'
                ],
                categories: [
                    'Climate', 'Ecosystems', 'Forestry', 'Farming', 'Plant Growth',
                    'Prevention'
                ],
                requirements: '1 scene with NIR-1 and red band information',
                createdAt: (new Date()).toISOString(),
                modifiedAt: (new Date()).toISOString()
            }]
        };
    }
}
