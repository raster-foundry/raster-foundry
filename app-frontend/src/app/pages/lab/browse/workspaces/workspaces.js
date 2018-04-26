/* global BUILDCONFIG */
import {Set} from 'immutable';
class LabBrowseWorkspacesController {
    constructor(
        $state, workspaceService, authService, localStorage, modalService
    ) {
        'ngInject';
        this.$state = $state;
        this.workspaceService = workspaceService;
        this.authService = authService;
        this.localStorage = localStorage;
        this.modalService = modalService;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.defaultSortingDirection = 'desc';
        this.defaultSortingField = 'modifiedAt';
        this.initSorting();
        // eslint-disable-next-line
        this.fetchWorkspaceList(undefined, this.$state.params.page);
        this.selected = new Set();
    }

    initSorting() {
        const sortString = this.fetchSorting();
        if (sortString) {
            const sort = this.deserializeSort(sortString);
            this.sortingField = sort.field || this.defaultSortingField;
            this.sortingDirection = sort.direction || this.defaultSortingDirection;
        } else {
            this.sortingField = this.defaultSortingField;
            this.sortingDirection = this.defaultSortingDirection;
        }
    }

    fetchWorkspaceList(searchVal, page = 1) {
        this.loadingWorkspaces = true;
        const params = {
            pageSize: 10,
            page: page - 1,
            sort: this.serializeSort()
        };
        if (searchVal) {
            params.search = searchVal;
        }
        this.workspaceService.fetchWorkspaces(params).then(d => {
            this.currentPage = page;
            this.updatePagination(d);
            let replace = !this.$state.params.page;
            this.$state.transitionTo(
                this.$state.$current.name,
                {page: this.currentPage},
                {
                    location: replace ? 'replace' : true,
                    notify: false
                }
            );
            this.lastWorkspaceResponse = d;
            this.workspacesList = d.results;
            this.loadingWorkspaces = false;
        });
    }

    updatePagination(data) {
        this.pagination = {
            show: data.count > data.pageSize,
            count: data.count,
            currentPage: data.page + 1,
            startingItem: data.page * data.pageSize + 1,
            endingItem: Math.min((data.page + 1) * data.pageSize, data.count),
            hasNext: data.hasNext,
            hasPrevious: data.hasPrevious
        };
    }

    formatWorkspaceVisibility(visibility) {
        const v = visibility.toUpperCase();
        if (v === 'PUBLIC') {
            return 'Public';
        }
        return 'Private';
    }

    serializeSort() {
        return `${this.sortingField},${this.sortingDirection}`;
    }

    deserializeSort(sortString) {
        const splitSortString = sortString.split(',');
        return {
            field: splitSortString[0],
            direction: splitSortString[1]
        };
    }

    fetchSorting() {
        const k = `${this.authService.getProfile().nickname}-workspace-sort`;
        return this.localStorage.getString(k);
    }

    storeSorting() {
        const k = `${this.authService.getProfile().nickname}-workspace-sort`;
        return this.localStorage.setString(k, this.serializeSort());
    }

    onSortChange(field) {
        if (field === this.sortingField) {
            // Toggle sorting direction if the same field is being used
            this.sortingDirection =
                this.sortingDirection === 'asc' ? 'desc' : 'asc';
        } else {
            this.sortingField = field;
            this.sortingDirection = this.defaultSortingDirection;
        }
        this.storeSorting();
        // eslint-disable-next-line
        this.fetchWorkspaceList(undefined, this.currentPage);
    }

    deleteSelected() {
        const modal = this.modalService.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => `Delete ${this.selected.size} workspaces?`,
                content: () => 'Deleting workspaces will make any ' +
                    'further tile requests with them fail',
                confirmText: () => 'Delete Workspaces',
                cancelText: () => 'Cancel'
            }
        });

        modal.result.then(() => {
            this.selected.forEach((id) => {
                this.workspaceService.deleteWorkspace(id).then(() => {
                    this.selected = this.selected.delete(id);
                    if (this.selected.size === 0) {
                        this.fetchWorkspaceList();
                    }
                }, () => {
                    this.fetchWorkspaceList();
                });
            });
        });
    }

    toggleWorkspaceSelection(id) {
        if (this.selected.has(id)) {
            this.selected = this.selected.delete(id);
        } else {
            this.selected = this.selected.add(id);
        }
    }

    openTemplateCreateModal() {
        this.modalService.open({
            component: 'rfTemplateCreateModal'
        });
    }

    search(value) {
        this.searchString = value;
        if (this.searchString) {
            this.fetchWorkspaceList(this.searchString);
        } else {
            this.fetchWorkspaceList();
        }
    }
}

export default angular.module('pages.lab.browse.workspaces', [])
    .controller('LabBrowseWorkspacesController', LabBrowseWorkspacesController);
