/* globals document */
import angular from 'angular';
import slider from 'angularjs-slider';
import typeahead from 'angular-ui-bootstrap/src/typeahead';
import _ from 'lodash';
import {Set} from 'immutable';

import sceneFilterTpl from './sceneFilterPane.html';

const SceneFilterPaneComponent = {
    templateUrl: sceneFilterTpl,
    controller: 'SceneFilterPaneController',
    bindings: {
        opened: '=',
        // array of repository name + service objects
        repositories: '<',
        // returns function fetchScenes(page, bbox, timestamp)
        onRepositoryChange: '&?'
    }
};

class FilterPaneController {
    constructor(
        $log, $q, $scope, $rootScope, $compile, $element, $timeout, $location, $state
    ) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$timeout = $timeout;
        this.$compile = $compile;
        this.$location = $location;
        this.currentRepository = _.first(this.repositories);
        this.filterComponents = [];
        this.initializedFilters = new Set();
        this.firstReset = true;
        this.$state = $state;

        this.$scope.$watch('$ctrl.opened', (opened) => {
            if (opened) {
                this.$timeout(() => this.$rootScope.$broadcast('reCalcViewDimensions'), 50);
            }
        });
    }

    $onInit() {
        this.debouncedOnRepositoryChange = _.debounce(this.onRepositoryChange, 250);
    }

    $onChanges(changes) {
        if (changes.onRepositoryChange && changes.onRepositoryChange.currentValue) {
            if (this.currentRepository) {
                this.debouncedOnRepositoryChange = _.debounce(this.onRepositoryChange, 250);
                this.onFilterChange();
            }
        }
        if (changes.repositories && changes.repositories.currentValue) {
            const repositories = changes.repositories.currentValue;
            if (repositories.length) {
                let repository = _.first(repositories);
                const repositoryParam = this.$location.search().repository;
                if (repositoryParam) {
                    repository = _.first(
                        _.filter(repositories, (repo) => repo.label === repositoryParam)
                    );
                }
                this.setRepository(repository);
            }
        }
    }

    onClose() {
        this.opened = false;
    }

    setRepository(repository) {
        this.repositoryError = false;
        this.settingRepository = true;
        repository.service.initRepository().then(() => {
            this.settingRepository = false;
            this.$location.search('repository', repository.label);
            this.currentRepository = repository;
            this.resetFilters();
        }, () => {
            this.repositoryError = true;
            this.settingRepository = false;
        });
    }

    resetFilters() {
        if (!this.firstReset) {
            let resetParams = _.keys(_.omit(this.$location.search(), ['bbox', 'repository']));
            resetParams.forEach((param) => this.$location.search(param, null));
        } else {
            this.firstReset = false;
        }

        this.filters = this.currentRepository.service.getFilters();
        this.filterParams = {};
        this.initializedFilters = this.initializedFilters.clear();
        this.filterComponents.forEach(({element, componentScope}) => {
            componentScope.$destroy();
            element.remove();
        });
        this.filterComponents = this.filters.map((filter) => {
            return this.createFilterComponent(filter);
        });
        const container = angular.element(document.querySelector('#filters'));
        this.filterComponents.forEach((filterComponent) => {
            container.append(filterComponent.element);
        });
    }

    onFilterChange(filter, filterParams) {
        if (filter) {
            this.filterParams = Object.assign({}, this.filterParams, filterParams);
            this.initializedFilters = this.initializedFilters.add(filter.label);
        }

        _.toPairs(this.filterParams).forEach(([param, val]) => {
            if (val !== null && typeof val === 'object') {
                this.$location.search(param, val.id);
            } else {
                this.$location.search(param, val);
            }
        });

        this.debouncedOnRepositoryChange({
            fetchScenes: this.currentRepository.service.fetchScenes(
                this.filterParams, this.$state.params.projectid
            ),
            repository: this.currentRepository
        });
    }

    createFilterComponent(filter) {
        const componentScope = this.$scope.$new(true, this.$scope);
        componentScope.filter = filter;
        componentScope.onFilterChange = this.onFilterChange.bind(this);
        const template = `<rf-${filter.type}-filter
                           class="filter-group"
                           data-filter="filter"
                           on-filter-change="onFilterChange(filter, filterParams)">
                          </rf-${filter.type}-filter>`;
        const element = this.$compile(template)(componentScope);
        return {
            element,
            componentScope
        };
    }
}

const SceneFilterPaneModule = angular.module('components.scenes.sceneFilterPane',
                                             [slider, typeahead]);

SceneFilterPaneModule.component('rfSceneFilterPane', SceneFilterPaneComponent);
SceneFilterPaneModule.controller('SceneFilterPaneController', FilterPaneController);

export default SceneFilterPaneModule;
