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
    constructor($scope, $state, $uibModal, moment, projectService) {
        'ngInject';
        this.$parent = $scope.$parent.$ctrl;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.Moment = moment;
        this.updateFrequencies = updateFrequencies;
        this.projectService = projectService;
    }

    $onInit() {
        this.showFilters = false;
        this.projectLoaded = false;
        this.aoiProjectParameters = {};
        this.aoiParamters = {};
        this.$parent.fetchProject().then(project => {
            this.project = project;
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
        // Ensure the project is available before we save
        this.$parent.fetchProject().then(srcProject => {
            const projectToSave = Object.assign(srcProject, this.aoiProjectParameters);
            this.projectService.updateProject(projectToSave).then(() => {
                // @TODO: this code can be reactivated once we have shapes to give to the backend
                // the promise above returns the project
                //
                // const aoiToCreate = Object.assign(this.aoiParameters, { projectId: project.id });
                // this.projectService.createAOI(aoiToCreate).then(() => {
                this.$state.go('projects.edit');
                // });
            });
        });
    }
}
