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
        this.showFilters = false;
        this.projectLoaded = false;
        this.aoiProjectParameters = {};

        this.shapeParams = {
            type: 'shape',
            label: 'Area of Interest',
            param: 'shape'
        };

        this.$q.all({
            project: this.projectEditService.fetchCurrentProject(),
            aoi: this.fetchProjectAOIs()
        }).then((result) => {
            ({project: this.project, aoi: {aoi: this.aoi, shape: this.shape}} = result);
            if (this.aoi) {
                this.aoiParameters = this.aoi.filters;
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

    fetchProjectAOIs() {
        return this.projectService.getProjectAois(
            this.$parent.projectId
        ).then((response) => {
            this.disableStartTimeChange = response.count;
            this.lastAoiResponse = response;
            if (response.count === 1) {
                this.projectAoi = _.first(response.results);
                return this.$q.all({
                    aoi: this.projectAoi,
                    shape: this.shapesService.fetchShapes({id: this.projectAoi.shape})
                });
            } else if (response.count > 1) {
                this.unsupportedAois = true;
                return this.$q.reject(new Error('Multiple AOI\'s are not currently supported.'));
            }
            return this.$q.resolve({});
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
        if (this.projectAoi) {
            let aoiToUpdate = Object.assign(
                _.clone(this.projectAoi),
                {
                    filters: aoiFilters,
                    isActive: isActive,
                    shape: this.shape.id
                }
            );
            return this.aoiService.updateAOI(aoiToUpdate);
        }
        let newAOI = {
            owner: this.authService.getProfile().sub,
            shape: this.shape.id,
            filters: aoiFilters,
            isActive: isActive,
            startTime: this.aoiProjectParameters.aoisLastChecked,
            approvalRequired: false
        };
        return this.projectService.createAOI(this.project.id, newAOI);
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
        const frequency = this.updateFrequencies
              .find(f => f.value === frequencyValue);
        return frequency && frequency.label;
    }

    getCurrentStartTime() {
        return this.aoiProjectParameters.aoisLastChecked &&
            this.aoiProjectParameters.aoisLastChecked.format('MM/DD/YY');
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
                return this.updateProjectAOI(
                    this.aoiPolygons, this.aoiParameters,
                    this.projectAoi ? this.projectAoi.isActive : true
                );
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

    onShapeChange(filter, filterParams) {
        this.shape = filterParams.shape;
        this.aoiPolygons = {geom: filterParams.geometry};
    }

    onCloseFilterPane(showFilterPane) {
        this.showFilters = showFilterPane;
    }

    toggleAoiActive() {
        if (this.projectAoi) {
            this.projectAoi.isActive = !this.projectAoi.isActive;
        }
    }
}

const AOIParametersModule = angular.module('page.projects.edit.aoi-parameters', []);

AOIParametersModule.controller('AOIParametersController', AOIParametersController);

export default AOIParametersModule;
