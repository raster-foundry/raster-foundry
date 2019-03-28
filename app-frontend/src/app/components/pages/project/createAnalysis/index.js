/* global BUILDCONFIG */
import { Set, Map } from 'immutable';
import L from 'leaflet';
import _ from 'lodash';
import tpl from './index.html';

class ProjectCreateAnalysisPageController {
    constructor(
        $rootScope, $q, $state, $timeout, uuid4,
        projectService, paginationService, analysisService, modalService, userService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
        this.BUILDCONFIG = BUILDCONFIG;
    }

    $onInit() {
        this.currentOwnershipFilter = this.$state.params.ownership || '';
        this.selected = new Set();
        this.itemActions = new Map();
        this.layerActions = new Map();
        this.fetchLayers();
        this.templateCreators = new Map();
        this.fetchTemplates();
    }

    fetchLayers() {
        if (this.layers && this.layers.length) {
            this.layerList = this.layers.map(this.toLayerInfo);
            this.layerActions = new Map(this.layerList.map(this.createLayerActions.bind(this)));
        } else {
            this.currentQuery = this.projectService
                .getProjectLayer(this.project.id, this.project.defaultLayerId)
                .then((defaultLayer) => {
                    delete this.currentQuery;
                    this.layerList = [this.toLayerInfo(defaultLayer)];
                    this.layerActions = new Map(
                        this.layerList.map(this.createLayerActions.bind(this)));
                }).catch(e => {
                    delete this.currentQuery;
                    this.fetchError = e;
                });
        }
    }

    createLayerActions(layer) {
        const hasGeom = _.get(layer, 'geometry.type');
        let actions = [];
        if (!hasGeom) {
            actions.push({
                icon: 'icon-warning color-danger',
                tooltip: 'No AOI defined for this layer',
                name: 'No AOI',
                callback: () => {},
                menu: false
            });
        }
        return [layer.id, actions];
    }

    toLayerInfo(layer) {
        return {
            id: layer.id,
            name: layer.name,
            subtext: layer.subtext,
            date: layer.createdAt,
            colorGroupHex: layer.colorGroupHex,
            geometry: layer.geometry
        };
    }

    handleOwnershipFilterChange() {
        this.fetchTemplates(1);
    }

    fetchTemplates(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.templateFetchError;
        this.templateList = [];
        // TODO add sort filters

        let params = {
            sort: 'createdAt,desc',
            pageSize: 10,
            page: page - 1,
            singleSource: true
        };

        if (this.search) {
            params.search = this.search;
        }
        if (this.currentOwnershipFilter) {
            params.ownershipType = this.currentOwnershipFilter;
        }

        let currentQuery = this.analysisService.fetchTemplates(params).then(paginatedResponse => {
            this.templateList = paginatedResponse.results;
            this.itemActions = new Map(this.templateList.map(this.addItemActions.bind(this)));
            this.fetchCreators(this.templateList).then((creators) => {
                if (creators.has(_.get(_.first(this.templateList), 'id'))) {
                    this.templateCreators = creators;
                }
            });
            // TODO fetch template owner information using
            // userService like in templateItem.module.js
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageparam(page, this.search, null, {
                ownership: this.currentOwnershipFilter
            });
            if (this.currentTemplateQuery === currentQuery) {
                delete this.templateFetchError;
            }
        }).catch(e => {
            if (this.currentQuery === currentQuery) {
                this.templateFetchError = e;
            }
        }).finally(() => {
            if (this.currentTemplateQuery === currentQuery) {
                delete this.currentTemplateQuery;
            }
        });

        this.currentTemplateQuery = currentQuery;
    }

    fetchCreators(templates) {
        const ownerPromises = templates.map(template => {
            if (template.owner === 'default') {
                return this.$q.resolve([template.id, this.BUILDCONFIG.APP_NAME]);
            }
            return this.userService.getUserById(template.owner).then(user => {
                const owner = user.personalInfo.firstName.trim() &&
                    user.personalInfo.lastName.trim() ?
                    `${user.personalInfo.firstName.trim()} ${user.personalInfo.lastName.trim()}` :
                    user.name || 'Anonymous';
                return [template.id, owner];
            }).catch(e => {
                return [template.id, this.BUILDCONFIG.APP_NAME];
            });
        });
        return this.$q.all(ownerPromises).then(
            (templatesAndOwners) => {
                return new Map(
                    templatesAndOwners
                        .filter(([id, owner]) => owner)
                        .map(([id, owner]) => {
                            return [id, `Created by: ${owner}`];
                        })
                );
            }
        );
    }

    addItemActions(item) {
        let actions = [{
            icon: 'icon-share',
            name: 'View algorithm',
            tooltip: 'View algorithm',
            callback: (event) => this.confirmViewAnalysis(event, item),
            menu: false
        }];
        if (item.description) {
            actions.push({
                icon: 'icon-help',
                name: 'Description',
                tooltip: item.description,
                menu: false
            });
        }
        return [item.id, actions];
    }

    isSelected(layerId) {
        return this.selected.has(layerId);
    }

    onSelect(layerId) {
        if (this.isSelected(layerId)) {
            this.selected = this.selected.delete(layerId);
        } else {
            this.selected = this.selected.add(layerId);
        }
    }

    confirmViewAnalysis(event, template) {
        event.preventDefault();
        event.stopPropagation();
        if (this.creationPromise) {
            return;
        }
        const modal = this.modalService.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'View analysis template',
                subtitle: () => 'Leave layer analysis creation to view template?',
                content: () =>
                    '<div class="text-center">' +
                    'Viewing the analysis template will cancel analysis creation.' +
                    '</div>',
                confirmText: () => 'Continue',
                cancelText: () => 'Go back'
            }
        });
        modal.result.then(() => {
            this.$state.go('lab.startAnalysis', {templateid: template.id});
        }).catch(() => {
        });
    }

    onAnalysisClick(event, template) {
        event.preventDefault();
        event.stopPropagation();
        if (this.creationPromise) {
            return;
        }
        let layersWithoutAOI = this.layers.filter(l => !_.get(l, 'geometry.type'));
        if (layersWithoutAOI.length) {
            this.aoiRequiredModal(layersWithoutAOI[0]);
        } else {
            const modal = this.modalService.open({
                component: 'rfConfirmationModal',
                resolve: {
                    title: () => 'Create analyses',
                    content: () =>
                        '<div class="text-center">' +
                        'This will create new analysis for each layer you\'ve selected on' +
                        `your project using the "${template.title}" template` +
                        '</div>',
                    confirmText: () => 'Create analyses',
                    cancelText: () => 'Go back'
                }
            });
            modal.result.then(() => {
                const creationPromises = this.layerList.map((layer) => {
                    const layerTemplate = this.createLayerAnalysis(layer, template);
                    return this.analysisService.createAnalysis(layerTemplate);
                });
                this.creationPromise = this.$q.all(creationPromises);
                return this.creationPromise;
            }).then(() => {
                this.$state.go('project.analyses');
            }).catch((error) => {
                if (!error || typeof error === 'string' &&
                    (error.includes('backdrop') ||
                     error.includes('escape'))
                ) {
                    return;
                }
                this.createError = true;
                this.$timeout(() => {
                    delete this.createError;
                }, 10000);
            }).finally(() => {
                delete this.creationPromise;
            });
        }
    }

    createLayerAnalysis(layer, template) {
        // get layer datasources?
        // note: just set all of them to 0 at the start. they will be changed later
        // use template to create an analysis for layer
        const analysis = {
            id: this.uuid4.generate(),
            name: `${template.title}: ${layer.name}`,
            visibility: 'PRIVATE',
            projectId: this.project.id,
            projectLayerId: layer.id,
            templateId: template.id,
            executionParameters: layer.geometry ? {
                id: this.uuid4.generate(),
                args: [_.cloneDeep(template.definition)],
                mask: this.reprojectGeometry(layer.geometry),
                apply: 'mask',
                metadata: {
                    label: 'Layer Mask',
                    collapsable: false
                }
            } : _.cloneDeep(template.definition)
        };
        let root = analysis.executionParameters;
        let nodes = root.args ? [...root.args] : [];
        while (nodes.length) {
            const node = nodes.pop();
            if (node.args) {
                nodes.push(...node.args);
            }
            if (['projectSrc', 'layerSrc'].includes(_.get(node, 'type'))) {
                Object.assign(node, {
                    type: 'layerSrc',
                    band: 0,
                    layerId: layer.id
                });
            }
        }
        return analysis;
    }

    reprojectGeometry(geom) {
        // assume single multipolygon feature
        if (geom.type.toLowerCase() !== 'multipolygon') {
            throw new Error('Tried to reproject a shape that isn\'t a multipolygon');
        }

        const polygons = geom.coordinates[0];
        const reprojected = Object.assign({}, geom, {
            coordinates: [
                polygons.map(
                    polygon =>
                        polygon
                            .map(([lng, lat]) => L.CRS.EPSG3857.project({lat, lng}))
                            .map(({x, y}) => [x, y])
                )
            ]
        });
        return reprojected;
    }

    removeLayers() {
        this.layerList = _.filter(this.layerList, (l) => !this.selected.has(l.id));
        this.selected = this.selected.clear();
    }

    selectAll() {
        if (this.allVisibleSelected()) {
            this.selected = this.selected.clear();
        } else {
            this.selected = this.selected.union(new Set(this.layerList.map(l => l.id)));
        }
    }

    allVisibleSelected() {
        return this.layerList &&
            this.selected.size &&
            this.selected.size === this.layerList.length;
    }

    aoiRequiredModal(layer) {
        this.modalService
            .open({
                component: 'rfFeedbackModal',
                resolve: {
                    title: () => 'No AOI defined',
                    content: () =>`
                        <h2>
                            Creating an analyses requires an AOI
                        </h2>
                        <p>
                            At least one selected layer does not have an AOI defined.
                            Click on the missing AOI warning icon
                            (<i class="icon-warning color-danger"></i>) to define an AOI.
                        </p>
                    `,
                    feedbackIconType: () => 'warning',
                    feedbackIcon: () => 'icon-warning',
                    confirmText: () => 'OK'
                }
            })
            .catch(() => {});
    }

    goToAoiDef(id) {
        this.$state.go('project.layer.aoi', {layerId: id, projectId: this.project.id});
    }
}

const component = {
    bindings: {
        user: '<',
        project: '<',
        layers: '<'
    },
    templateUrl: tpl,
    controller: ProjectCreateAnalysisPageController.name
};

export default angular
    .module('components.pages.projects.createAnalysis', [])
    .controller(ProjectCreateAnalysisPageController.name, ProjectCreateAnalysisPageController)
    .component('rfProjectCreateAnalysisPage', component)
    .name;
