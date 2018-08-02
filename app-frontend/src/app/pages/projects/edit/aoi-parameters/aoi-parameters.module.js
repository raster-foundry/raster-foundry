import angular from 'angular';

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

const startTimeTooltip = 'Start time may not be changed once a monitoring has started.';

class AOIParametersController {
    constructor(
        $log, $q, $scope, $state, modalService, $timeout,
        moment, projectService, aoiService, authService, mapService,
        projectEditService, shapesService
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.$parent = $scope.$parent.$ctrl;
        this.updateFrequencies = updateFrequencies;

        this.getMap = () => mapService.getMap('edit');
        this.startTimeTooltip = startTimeTooltip;
    }

    $onInit() {
        this.$scope.$on('$destroy', () => {
            this.getMap().then(mapWrapper => mapWrapper.deleteLayers('Areas Of Interest'));
        });

        this.showFilters = false;
        this.projectLoaded = false;
        this.isProjectAoisDrawn = false;
        this.aoiProjectParameters = {};
        this.drawOptions = {
            areaType: 'interest',
            requirePolygons: true
        };

        this.$q.all({
            project: this.projectEditService.fetchCurrentProject(),
            aoi: this.fetchProjectAOIs()
        }).then((result) => {
            this.project = result.project;
            if (result.aoi) {
                this.aoiParameters = result.aoi.filters;
            } else {
                this.aoiParameters = {
                    'orgParams': {
                        'organizations': []
                    },
                    'userParams': {},
                    'imageParams': {
                        'scene': []
                    },
                    'sceneParams': {
                        'month': [],
                        'datasource': [],
                        'ingestStatus': [],
                        'maxCloudCover': 10
                    },
                    'timestampParams': {}
                };
            }

            this.aoiProjectParameters = {
                // Default cadence on frontend
                aoiCadenceMillis: this.project.aoiCadenceMillis ||
                    604800000,
                // Default to start of day if not set on project
                aoisLastChecked: this.moment(this.project.aoisLastChecked) ||
                    this.moment().startOf('day')
            };
            this.projectLoaded = true;
        });
    }

    drawProjectAois(multipolygon) {
        this.getMap().then((map) => {
            let aoiLayer = L.geoJSON(multipolygon, {
                style: () => {
                    return {
                        weight: 2,
                        fillOpacity: 0.2
                    };
                }
            });
            map.setLayer(
                'Areas Of Interest',
                aoiLayer,
                true
            );
            let bounds = aoiLayer.getBounds();
            if (bounds.isValid()) {
                map.map.fitBounds(bounds, {
                    padding: [35, 35]
                });
            }
        });
        this.isProjectAoisDrawn = true;
    }

    fetchProjectAOIs() {
        return this.projectService.getProjectAois(
            this.$parent.projectId
        ).then((response) => {
            this.disableStartTimeChange = response.count;
            this.lastAoiResponse = response;
            if (response.count === 1) {
                this.projectAoi = _.first(response.results);
                return this.$q.all([
                    this.projectAoi, this.shapesService.fetchShapes({id: this.projectAoi.shape})
                ]);
            } else if (response.count > 1) {
                this.unsupportedAois = true;
                return this.$q.reject(new Error('Multiple AOI\'s are not currently supported.'));
            }
            return this.$q.resolve([]);
        }).then((results) => {
            let [aoi, shape] = results;
            if (aoi && shape) {
                this.aoiPolygons = {geom: shape.geometry};
                if (!this.isProjectAoisDrawn) {
                    this.drawProjectAois(this.aoiPolygons.geom);
                }
            }
            return aoi;
        }).catch((error) => {
            if (error instanceof Error) {
                this.$log.error(error.message);
            } else {
                this.$log.error(`Error fetching project AOIs: ${error}`);
            }
            this.$q.reject(error);
        });
    }

    updateProjectAOI(multipolygon, aoiFilters, isActive) {
        if (this.unsupportedAois === true) {
            return this.$q.reject('Projects with multiple AOI\'s are not currently supported');
        }
        return this.shapesService.createShape({
            type: 'FeatureCollection',
            features: [
                {
                    type: 'Feature',
                    geometry: {
                        'type': multipolygon.geom.type,
                        'coordinates': multipolygon.geom.coordinates,
                        'srid': multipolygon.geom.srid
                    },
                    properties: {
                        name: `AOI: ${this.project.name} ${(new Date()).toLocaleString()}`
                    }
                }
            ]
        }).then(([shape]) => {
            if (this.projectAoi) {
                let aoiToUpdate = Object.assign(
                    _.clone(this.projectAoi),
                    {
                        filters: aoiFilters,
                        isActive: isActive,
                        shape: shape.id
                    }
                );
                return this.$q.all([shape, this.aoiService.updateAOI(aoiToUpdate)]);
            }
            let newAOI = {
                owner: this.authService.getProfile().sub,
                shape: shape.id,
                filters: aoiFilters,
                isActive: isActive,
                startTime: this.aoiProjectParameters.aoisLastChecked,
                approvalRequired: false
            };
            return this.$q.all([shape, this.projectService.createAOI(this.project.id, newAOI)]);
        });
    }

    toggleFilters() {
        this.showFilters = !this.showFilters;
    }

    onFilterChange(changes) {
        let newParameters = Object.assign({}, this.aoiParameters);
        Object.keys(changes).forEach((changeProperty) => {
            if (changes[changeProperty] !== null) {
                newParameters.sceneParams[changeProperty] = changes[changeProperty];
            } else {
                delete newParameters.sceneParams[changeProperty];
            }
        });
        this.aoiParameters = newParameters;
    }

    openDatePickerModal() {
        this.modalService
            .open({
                component: 'rfDatePickerModal',
                windowClass: 'auto-width-modal',
                resolve: {
                    config: () => Object({
                        selectedDay: this.aoiProjectParameters.aoisLastChecked
                    })
                }
            }).result.then(selectedDay => {
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
        delete this.saveSuccessful;
        delete this.error;
        this.saveParametersRequest = this.projectEditService.fetchCurrentProject()
            .then(srcProject => {
                const projectToSave = Object.assign(srcProject, this.aoiProjectParameters);
                return this.projectService.updateProject(projectToSave);
            })
            .then(() => {
                return this.updateProjectAOI(this.aoiPolygons, this.aoiParameters, true);
            })
            .then(()=>{
                this.saveSuccessful = true;
                this.$timeout(() => {
                    delete this.saveSuccessful;
                }, 3000);
                // this.$state.go('projects.edit');
            }).catch((error) => {
                this.error = error;
            }).then(() => {
                this.fetchProjectAOIs();
            });
    }

    isSavingParameters() {
        return this.saveParametersRequest && this.saveParametersRequest.$$state.status === 0;
    }

    startDrawing() {
        this.drawing = true;
        this.getMap().then((mapWrapper) => {
            mapWrapper.hideLayers('Areas Of Interest', false);
        });
    }

    onAoiSave(multipolygon) {
        this.drawing = false;
        this.aoiPolygons = multipolygon;
        this.drawProjectAois(multipolygon.geom);
    }

    onAoiCancel() {
        this.drawing = false;
        this.getMap().then((mapWrapper) => {
            if (mapWrapper.getLayers('Areas Of Interest').length) {
                mapWrapper.showLayers('Areas Of Interest', true);
            }
        });
    }

    onCloseFilterPane(showFilterPane) {
        this.showFilters = showFilterPane;
    }
}

const AOIParametersModule = angular.module('page.projects.edit.aoi-parameters', []);

AOIParametersModule.controller('AOIParametersController', AOIParametersController);

export default AOIParametersModule;
