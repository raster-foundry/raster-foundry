/* globals _ */
const updateFrequencies = [
    {
        label: 'every 6 hours',
        value: 21600000
    },
    {
        label: 'every 12 hours',
        value: 43200000
    },
    {
        label: 'every day',
        value: 86400000
    },
    {
        label: 'every week',
        value: 604800000
    },
    {
        label: 'every two weeks',
        value: 1209600000
    },
    {
        label: 'every month',
        value: 2592000000
    }
];

// @TODO: when we have the backend available to handle this, uncomment
/*
const addMethods = [
    {
        label: 'added when found',
        value: 'AUTOMATIC'
    },
    {
        label: 'added when approved',
        value: 'MANUAL'
    }
];
*/

export default class AOIParametersController {
    constructor(
        $log, $q, $scope, $state, $uibModal,
        moment, projectService, aoiService, authService, mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.Moment = moment;
        this.updateFrequencies = updateFrequencies;
        this.projectService = projectService;
        this.aoiService = aoiService;
        this.authService = authService;

        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        this.$scope.$on('$destroy', () => {
            this.getMap().then(mapWrapper => mapWrapper.deleteLayers('Areas Of Interest'));
        });

        this.showFilters = false;
        this.projectLoaded = false;
        this.isProjectAoisDrawn = false;
        this.aoiProjectParameters = {};
        this.aoiParamters = {};
        this.drawOptions = {
            areaType: 'interest',
            requirePolygons: true
        };

        this.$q.all({
            project: this.$parent.fetchProject(),
            aois: this.fetchProjectAOIs()
        }).then((result) => {
            this.project = result.project;
            this.aoiProjectParameters = {
                aoiCadenceMillis: this.project.aoiCadenceMillis ||
                    604800000,
                aoisLastChecked: this.Moment(this.project.aoisLastChecked) ||
                    this.Moment().startOf('day')
            };
            this.aoiParameters = {
                filters: {},
                area: null
            };
            this.projectLoaded = true;
        });
    }

    drawProjectAois(multipolygon) {
        this.getMap().then((map) => {
            map.setLayer(
                'Areas Of Interest',
                L.geoJSON(multipolygon.geom, {
                    style: () => {
                        return {
                            weight: 2,
                            fillOpacity: 0.2
                        };
                    }
                }),
                true
            );
        });
        this.isProjectAoisDrawn = true;
    }

    fetchProjectAOIs() {
        this.aoiRequest = this.projectService.getProjectAois(
            this.$parent.projectId
        ).then((response) => {
            this.projectAois = response.results || [];
            if (response.results && response.results.length === 1) {
                let aoi = _.first(this.projectAois);
                this.aoiPolygons = aoi.area;
                if (!this.isProjectAoisDrawn) {
                    this.drawProjectAois(this.aoiPolygons);
                }
                return response.results;
            } else if (response.results && response.results.length > 1) {
                this.unsupportedAois = true;
                return this.$q.reject('Multiple AOIs are currently not supported.');
            }

            return [];
        }, (error) => {
            this.$log.error('Error fetching project aois', error);
        });
    }

    updateProjectAOIs(multipolygon, aoiFilters) {
        if (this.projectAois && this.projectAois.length === 1) {
            let aoiToUpdate = this.projectAois[0];
            aoiToUpdate.area = multipolygon;
            aoiToUpdate.filters = aoiFilters;
            this.aoiService.updateAOI(aoiToUpdate).then(() => {
                this.fetchProjectAOIs();
            });
        } else if (this.projectAois && !this.projectAois.length) {
            let newAOI = {
                owner: this.authService.profile().user_id,
                area: multipolygon,
                filters: aoiFilters
            };
            this.projectService.createAOI(this.project.id, newAOI).then(() => {
                this.fetchProjectAOIs();
            });
        } else {
            this.$log.error('Tried to update an aoi in a project with more' +
                            'than 1 aoi. This is not currently supported');
        }
    }

    toggleFilters() {
        this.showFilters = !this.showFilters;
    }

    openDatePickerModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfDatePickerModal',
            windowClass: 'auto-width-modal',
            resolve: {
                config: () => Object({
                    selectedDay: this.aoiProjectParameters.aoisLastChecked
                })
            }
        });

        this.activeModal.result.then(
            selectedDay => {
                this.updateStartDate(selectedDay);
            });
    }

    updateFrequency(ms) {
        this.aoiProjectParameters.aoiCadenceMillis = ms;
    }

    updateStartDate(sd) {
        this.aoiProjectParameters.aoisLastChecked = sd;
    }

    getCurrentFrequency() {
        const frequencyValue = this.aoiProjectParameters.aoiCadenceMillis;
        return this.updateFrequencies
            .find(f => f.value === frequencyValue)
            .label;
    }

    getCurrentStartTime() {
        return this.aoiProjectParameters.aoisLastChecked.format('MM/DD/YY');
    }

    saveParameters() {
        this.$parent.fetchProject().then(srcProject => {
            const projectToSave = Object.assign(srcProject, this.aoiProjectParameters);
            this.projectService.updateProject(projectToSave).then(() => {
                this.updateProjectAOIs(this.savedMultipolygon, this.aoiParameters.filters);
                this.$state.go('projects.edit');
            });
        });
    }

    startDrawing() {
        this.drawing = true;
        this.getMap().then((mapWrapper) => {
            mapWrapper.hideLayers('Areas Of Interest', false);
        });
    }

    onAoiSave(multipolygon) {
        this.drawing = false;
        this.savedMultipolygon = multipolygon;
        this.drawProjectAois(multipolygon);
    }

    onAoiCancel() {
        this.drawing = false;
        this.getMap().then((mapWrapper) => {
            if (mapWrapper.getLayers('Areas Of Interest').length) {
                mapWrapper.showLayers('Areas Of Interest', true);
            }
        });
    }
}
