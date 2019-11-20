import _ from 'lodash';

export default (entity, permissionsBase, entityType, platform, bufferChanges, afterSave) =>
    class permissionsUI {
        constructor(
            $rootScope,
            $scope,
            $element,
            $timeout,
            $q,
            permissionsService,
            organizationService,
            teamService,
            userService,
            authService
        ) {
            'ngInject';
            $rootScope.autoInject(this, arguments);
        }

        $onInit() {
            this.skipBuffer = !bufferChanges;
            this.objectOwnerId = entity.owner.id || entity.owner;
            this.userId = this.authService.user.id;

            this.loading = false;
            this.rawPermissions = [];
            this.actionsBuffer = {};
            this.entityCache = {
                organization: {},
                team: {},
                user: {}
            };

            this.actionTypes = [
                {
                    tag: 'viewNonScene',
                    label: 'Can view',
                    applies: o => o.toLowerCase() !== 'scene',
                    actions: ['VIEW'],
                    default: true
                },
                {
                    tag: 'viewScene',
                    label: 'Can view',
                    applies: o => o.toLowerCase() === 'scene',
                    actions: ['VIEW', 'DOWNLOAD'],
                    default: true
                },
                {
                    tag: 'annotate',
                    label: 'Can annotate',
                    applies: o => o.toLowerCase() === 'project',
                    actions: ['VIEW', 'ANNOTATE'].sort()
                },
                {
                    tag: 'edit',
                    label: 'Can edit',
                    applies: o => !['project', 'scene'].includes(o.toLowerCase()),
                    actions: ['VIEW', 'EDIT'].sort()
                },
                {
                    tag: 'editScene',
                    label: 'Can edit',
                    applies: o => o.toLowerCase() === 'scene',
                    actions: ['VIEW', 'EDIT', 'DOWNLOAD'].sort()
                },
                {
                    tag: 'editProject',
                    label: 'Can edit',
                    applies: o => o.toLowerCase() === 'project',
                    actions: ['VIEW', 'ANNOTATE', 'EDIT'].sort()
                },
                {
                    tag: 'delete',
                    label: 'Can delete',
                    applies: o => !['project', 'scene'].includes(o.toLowerCase()),
                    actions: ['VIEW', 'EDIT', 'DELETE'].sort()
                },
                {
                    tag: 'deleteScene',
                    label: 'Can delete',
                    applies: o => o.toLowerCase() === 'scene',
                    actions: ['VIEW', 'DOWNLOAD', 'EDIT', 'DELETE'].sort()
                },
                {
                    tag: 'deleteProject',
                    label: 'Can delete',
                    applies: o => o.toLowerCase() === 'project',
                    actions: ['VIEW', 'ANNOTATE', 'EDIT', 'DELETE'].sort()
                }
            ];

            this.subjectTypes = [
                {
                    name: 'Everyone',
                    target: 'PLATFORM',
                    id: 0,
                    applies: () =>
                        this.authService.user.isSuperuser ||
                        _.find(
                            this.authService.getUserRoles(),
                            userRole =>
                                userRole.groupType === 'PLATFORM' && userRole.groupRole === 'ADMIN'
                        )
                },
                {
                    name: 'An organization',
                    singular: 'organization',
                    plural: 'organizations',
                    target: 'ORGANIZATION',
                    id: 1,
                    applies: () => true
                },
                {
                    name: 'A team',
                    singular: 'team',
                    plural: 'teams',
                    target: 'TEAM',
                    id: 2,
                    applies: () => true
                },
                {
                    name: 'A user',
                    singular: 'user',
                    plural: 'users',
                    target: 'USER',
                    id: 3,
                    applies: () => true
                }
            ];

            this.defaultAction = this.actionTypes.find(a => a.default);

            this.authTarget = {
                permissionsBase: permissionsBase,
                objectType: entityType,
                objectId: entity.id
            };

            this.applicableActions = this.getApplicableActions(entityType);
            this.fetchPermissions();
        }

        fetchPermissions() {
            this.loading = true;
            return this.permissionsService
                .query(this.authTarget)
                .$promise.then(permissions => {
                    this.updatePermissions(permissions);
                    this.updateSubjects();
                    this.updateEntityCacheFromBuffer();
                })
                .finally(() => {
                    this.loading = false;
                });
        }

        updatePermissions(permissions) {
            this.actionsBuffer = this.permissionsToActions(permissions);
            this.permissionCount = this.countBufferedPermissions();
        }

        updateSubjects() {
            this.applicableSubjects = this.getApplicableSubjects();
            this.currentTargetSubject = this.getCurrentSubject().target;
        }

        updateEntityCacheFromBuffer() {
            if (_.get(this.actionsBuffer, 'ORGANIZATION')) {
                Object.keys(this.actionsBuffer.ORGANIZATION).forEach(id => {
                    this.fetchCachedOrganizationDetails(id);
                });
            }
            if (_.get(this.actionsBuffer, 'TEAM')) {
                Object.keys(this.actionsBuffer.TEAM).forEach(id => {
                    this.fetchCachedTeamDetails(id);
                });
            }
            if (_.get(this.actionsBuffer, 'USER')) {
                Object.keys(this.actionsBuffer.USER).forEach(id => {
                    this.fetchCachedUserDetails(id);
                });
            }
        }

        // Transform array of permissions to grouped action sets
        permissionsToActions(permissionSet) {
            const self = this;
            const permissionsByType = _.groupBy(permissionSet, p => p.subjectType);
            let permissionsByTypeAndId = {};

            Object.keys(permissionsByType).forEach(k => {
                const groupedPermissions = _.groupBy(permissionsByType[k], p => p.subjectId);

                let itemizedPermissions = {};

                Object.keys(groupedPermissions).forEach(j => {
                    itemizedPermissions[j] = this.permissionsToAction(groupedPermissions[j]);
                });

                permissionsByTypeAndId[k] = itemizedPermissions;
            });

            return permissionsByTypeAndId;
        }

        // Transform an array of permissions with matching subject to an action set
        permissionsToAction(permissions) {
            const actions = permissions.map(p => p.actionType).sort();
            return this.actionTypes.find(a => _.isEqual(a.actions, actions));
        }

        actionsToPermissions(actionSets) {
            return _.flattenDeep(
                Object.keys(actionSets).map(subjectType => {
                    return Object.keys(actionSets[subjectType]).map(subjectId => {
                        return this.actionToPermissions(
                            subjectType,
                            subjectId,
                            actionSets[subjectType][subjectId]
                        );
                    });
                })
            );
        }

        actionToPermissions(subjectType, subjectId, actionSet) {
            return actionSet.actions.map(a => ({
                objectType: this.authTarget.objectType,
                objectId: this.authTarget.objectId,
                subjectType,
                subjectId,
                actionType: a,
                isActive: true
            }));
        }

        countBufferedPermissions() {
            return Object.keys(this.actionsBuffer).reduce(
                (acc, k) => acc + Object.keys(this.actionsBuffer[k]).length,
                0
            );
        }

        getApplicableActions(objectType) {
            return this.actionTypes.filter(a => a.applies(objectType));
        }

        getApplicableSubjects() {
            return this.subjectTypes.filter(s => s.applies());
        }

        getCurrentSubject() {
            if (
                this.currentTargetSubject &&
                !!this.applicableSubjects.find(s => s.target === this.currentTargetSubject)
            ) {
                return this.applicableSubjects.find(s => s.target === this.currentTargetSubject);
            }
            return this.applicableSubjects[0];
        }

        fetchCachedOrganizationDetails(id) {
            if (!this.entityCache.organization[id]) {
                return this.organizationService.getOrganization(id).then(
                    organization => {
                        this.entityCache.organization[id] = organization;
                    },
                    error => {
                        if ([403, 404].includes(error.status)) {
                            this.entityCache.organization[id] = {
                                name: 'Private Organization',
                                private: true
                            };
                        } else {
                            this.entityCache.organization[id] = { error };
                        }
                    }
                );
            }
            return Promise.resolve();
        }

        fetchCachedTeamDetails(id) {
            if (!this.entityCache.team[id]) {
                this.teamService.getTeam(id).then(
                    team => {
                        this.entityCache.team[id] = team;
                        // Teams don't have their own logo, so we want to use the team's parent
                        // organization logo. We have to get the org to the the logo uri
                        this.fetchCachedOrganizationDetails(team.organizationId);
                        return team;
                    },
                    error => {
                        if ([403, 404].includes(error.status)) {
                            this.entityCache.team[id] = {
                                name: 'Private Team',
                                private: true
                            };
                        } else {
                            this.entityCache.team[id] = { error };
                        }
                    }
                );
            }
        }

        fetchCachedUserDetails(id) {
            if (!this.entityCache.user[id]) {
                this.userService.getUserById(id).then(
                    user => {
                        this.entityCache.user[id] = user;
                    },
                    error => {
                        if ([403, 404].includes(error.status)) {
                            this.entityCache.user[id] = {
                                name: 'Private User',
                                private: true
                            };
                        } else {
                            this.entityCache.user[id] = { error };
                        }
                    }
                );
            }
        }

        checkPotentialDuplication() {
            this.duplicateDetected =
                this.actionsBuffer[this.currentTargetSubject] &&
                this.actionsBuffer[this.currentTargetSubject][this.selectedSubjectId];
        }

        onSubjectTypeChange() {
            this.onClearSelection();
        }

        onActionsAdd() {
            this.actionsBuffer[this.currentTargetSubject] =
                this.actionsBuffer[this.currentTargetSubject] || {};
            if (this.currentTargetSubject === 'PLATFORM') {
                this.actionsBuffer.PLATFORM[platform.id] = this.defaultAction;
            } else if (this.selectedSubjectId) {
                this.actionsBuffer[this.currentTargetSubject][
                    this.selectedSubjectId
                ] = this.defaultAction;
            }
            this.permissionCount = this.countBufferedPermissions();
            this.updateSubjects();
            this.onClearSelection();
            this.updateEntityCacheFromBuffer();
            if (this.skipBuffer) {
                this.onSave();
            }
        }

        onActionsChange(subjectType, subjectId, actionTag) {
            const action = this.actionTypes.find(a => a.tag === actionTag);
            this.actionsBuffer[subjectType][subjectId] = action;
            this.permissionCount = this.countBufferedPermissions();
            this.updateSubjects();
            if (this.skipBuffer) {
                this.onSave();
            }
        }

        onActionsDelete(subjectType, subjectId) {
            delete this.actionsBuffer[subjectType][subjectId];
            this.permissionCount = this.countBufferedPermissions();
            this.updateSubjects();
            this.checkPotentialDuplication();
            if (this.skipBuffer) {
                this.onSave();
            }
        }

        onUserSearch(searchTerm) {
            if (searchTerm && searchTerm.length) {
                this.lastRequestTime = Date.now();
                const thisRequestTime = this.lastRequestTime;

                this.$q
                    .all({
                        user: this.authService.getCurrentUser(),
                        results: this.userService.searchUsers(searchTerm)
                    })
                    .then(({ user, results }) => {
                        // Only use results if the request is the most recent
                        if (this.lastRequestTime === thisRequestTime) {
                            this.suggestions = results
                                .filter(r => r.id !== user.id)
                                .map(r => ({
                                    label: r.name || r.email || r.id,
                                    avatar: r.profileImageUri,
                                    id: r.id
                                }));
                        }
                    })
                    .finally(() => {
                        // Only alter the loading flag if the request is the most recent
                        if (this.lastRequestTime === thisRequestTime) {
                            this.loading = false;
                        }
                    });
            } else {
                this.suggestions = [];
            }
        }

        onOrganizationSearch(searchTerm) {
            if (searchTerm && searchTerm.length) {
                this.lastRequestTime = Date.now();
                const thisRequestTime = this.lastRequestTime;
                this.organizationService
                    .searchOrganizations(searchTerm)
                    .then(results => {
                        // Only use results if the request is the most recent
                        if (this.lastRequestTime === thisRequestTime) {
                            this.suggestions = results.map(org => ({
                                label: org.name,
                                avatar: org.logoUri,
                                id: org.id
                            }));
                        }
                    })
                    .finally(() => {
                        // Only alter the loading flag if the request is the most recent
                        if (this.lastRequestTime === thisRequestTime) {
                            this.loading = false;
                        }
                    });
            } else {
                this.suggestions = [];
            }
        }

        // resolve.platform
        onTeamSearch(searchTerm) {
            if (searchTerm && searchTerm.length) {
                this.lastRequestTime = Date.now();
                const thisRequestTime = this.lastRequestTime;
                this.teamService
                    .searchTeams(searchTerm, platform.id)
                    .then(results => {
                        // Only use results if the request is the most recent
                        if (this.lastRequestTime === thisRequestTime) {
                            Promise.all(
                                results.map(t =>
                                    this.fetchCachedOrganizationDetails(t.organizationId)
                                )
                            ).then(() => {
                                this.$scope.$evalAsync(() => {
                                    // eslint-disable-next-line
                                    this.suggestions = results.map(team => ({
                                        label: team.name,
                                        avatar: _.get(
                                            this.entityCache,
                                            ['organization', team.organizationId, 'logoUri'],
                                            ''
                                        ),
                                        id: team.id
                                    }));
                                });
                            });
                            this.suggestions = results.map(team => ({
                                label: team.name,
                                avatar: '',
                                id: team.id
                            }));
                        }
                    })
                    .finally(() => {
                        // Only alter the loading flag if the request is the most recent
                        if (this.lastRequestTime === thisRequestTime) {
                            this.loading = false;
                        }
                    });
            } else {
                this.suggestions = [];
            }
        }

        onSubjectSelect(suggestedSubject) {
            this.selectedSubjectId = suggestedSubject.id;
            this.selectedSubject = suggestedSubject;
            this.checkPotentialDuplication();
        }

        onClearSelection() {
            delete this.duplicateDetected;
            delete this.selectedSubjectId;
            delete this.selectedSubject;
        }

        // afterSave
        onSave() {
            const rules = this.actionsToPermissions(this.actionsBuffer);
            if (rules.length) {
                this.permissionsService.update(this.authTarget, rules).then(() => afterSave());
            } else {
                this.permissionsService.delete(this.authTarget).then(() => afterSave());
            }
        }
    };
