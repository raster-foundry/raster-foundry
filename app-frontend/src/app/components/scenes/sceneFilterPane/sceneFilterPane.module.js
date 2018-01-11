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
        $log, $q, $scope, $rootScope, $compile, $element, $timeout, $location
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

        this.$scope.$watch('$ctrl.opened', (opened) => {
            if (opened) {
                this.$timeout(() => this.$rootScope.$broadcast('reCalcViewDimensions'), 50);
            }
        });
    }

    $onChanges(changes) {
        if (changes.onRepositoryChange && changes.onRepositoryChange.currentValue) {
            if (this.currentRepository) {
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
            switch (filter.type) {
            case 'searchSelect':
                return this.createSearchSelectComponent(filter);
            case 'daterange':
                return this.createDateRangeComponent(filter);
            case 'slider':
                return this.createSliderComponent(filter);
            case 'tagFilter':
                return this.createTagFilterComponent(filter);
            default:
                throw new Error(`Unrecognized filter type: ${filter.type}`);
            }
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

        if (this.initializedFilters.size === this.filterComponents.length) {
            _.toPairs(this.filterParams).forEach(([param, val]) => {
                this.$location.search(param, val);
            });
            this.onRepositoryChange({
                fetchScenes: this.currentRepository.service.fetchScenes(this.filterParams),
                repository: this.currentRepository
            });
        }
    }

    createSearchSelectComponent(filter) {
        const componentScope = this.$scope.$new(true, this.$scope);
        componentScope.filter = filter;
        componentScope.onFilterChange = this.onFilterChange.bind(this);
        const template = `<rf-search-select-filter
                            class="filter-group"
                            data-filter="filter"
                            on-filter-change="onFilterChange(filter, filterParams)">
                          </rf-search-select-filter>`;
        const element = this.$compile(template)(componentScope);
        return {
            element,
            componentScope
        };
    }

    createDateRangeComponent(filter) {
        const componentScope = this.$scope.$new(true, this.$scope);
        componentScope.filter = filter;
        componentScope.onFilterChange = this.onFilterChange.bind(this);
        const template = `<rf-daterange-filter
                            class="filter-group"
                            data-filter="filter"
                            on-filter-change="onFilterChange(filter, filterParams)">
                          </rf-daterange-filter>`;
        const element = this.$compile(template)(componentScope);
        return {
            element,
            componentScope
        };
    }

    createSliderComponent(filter) {
        const componentScope = this.$scope.$new(true, this.$scope);
        componentScope.filter = filter;
        componentScope.onFilterChange = this.onFilterChange.bind(this);
        const template = `<rf-slider-filter
                            class="filter-group"
                            data-filter="filter"
                            on-filter-change="onFilterChange(filter, filterParams)">
                          </rf-slider-filter>`;
        const element = this.$compile(template)(componentScope);
        return {
            element,
            componentScope
        };
    }

    createTagFilterComponent(filter) {
        const componentScope = this.$scope.$new(true, this.$scope);
        componentScope.filter = filter;
        componentScope.onFilterChange = this.onFilterChange.bind(this);
        const template = `<rf-tag-filter
                            class="filter-group"
                            data-filter="filter"
                            on-filter-change="onFilterChange(filter, filterParams)">
                          </rf-tag-filter>`;
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
