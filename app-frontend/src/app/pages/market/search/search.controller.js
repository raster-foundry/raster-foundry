export default class MarketSearchController {
    constructor( // eslint-disable-line max-params
        $log, $state
    ) {
        'ngInject';
        this.$state = $state;
        this.$log = $log;

        this.populatePlaceholderData();
        this.populateToolList();
    }

    populateToolList() {
        this.$log.log('Using placeholder data in Market search controller');
    }

    populatePlaceholderData() {
        this.searchTerms = ['NDVI', 'TERM', 'Vegetation', 'Azavea'];
        this.tags = [
            {
                label: 'Vegetation Index',
                selected: true
            },
            {
                label: 'Image Classification',
                selected: true
            }
        ];
        this.categories = [
            {
                label: 'Agriculture',
                selected: true
            },
            {
                label: 'Aggregates',
                selected: true
            }
        ];
        this.queryResult = {
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
                screenshots: [
                    {id: 1, url: 'https://placehold.it/800x480'},
                    {id: 2, url: 'https://placehold.it/800x480'},
                    {id: 3, url: 'https://placehold.it/800x480'}
                ],
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
                screenshots: [
                    {id: 1, url: 'https://placehold.it/800x480'},
                    {id: 2, url: 'https://placehold.it/800x480'},
                    {id: 3, url: 'https://placehold.it/800x480'}
                ],
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
                screenshots: [
                    {id: 1, url: 'https://placehold.it/800x480'},
                    {id: 2, url: 'https://placehold.it/800x480'},
                    {id: 3, url: 'https://placehold.it/800x480'}
                ],
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

    removeSearchTerm(index) {
        this.searchTerms.splice(index, 1);
    }

    clearSearch() {
        this.searchTerms = [];
    }

    toggleTag(index) {
        this.tags[index].selected = !this.tags[index].selected;
    }

    toggleCategory(index) {
        this.categories[index].selected = !this.categories[index].selected;
    }

    navTool(tool) {
        this.$state.go('market.tool', {id: tool.id, toolData: tool});
    }
}
