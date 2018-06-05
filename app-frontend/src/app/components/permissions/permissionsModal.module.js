import angular from 'angular';
import permissionsModalTpl from './permissionsModal.html';
import _ from 'lodash';

const PermissionsModalComponent = {
    templateUrl: permissionsModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'PermissionsModalController'
};

class PermissionsModalController {
    constructor(authService, organizationService, permissionsService, platformService,
                teamService, userService) {
        'ngInject';

        // services
        this.authService = authService;
        this.organizationService = organizationService;
        this.permissionsService = permissionsService;
        this.platformService = platformService;
        this.teamService = teamService;
        this.userService = userService;

        // data
        this.defaultActions = ['VIEW', 'EDIT', 'DELETE'];
        this.newPermissionSubject = null;
        this.subjectTypes = {
            EVERYONE: {
                name: 'Everyone',
                target: 'EVERYONE',
                id: 0
            },
            TEAM: {
                name: 'One of my teams',
                target: 'TEAM',
                id: 1
            },
            USER: {
                name: 'A user',
                target: 'USER',
                id: 2
            },
            ORGANIZATION: {
                name: 'My organization',
                target: 'ORGANIZATION',
                id: 3
            }
        };
        this.states = {
            existing: 'EXISTINGPERMISSIONS',
            newPermission: 'NEWPERMISSION',
            choosePermissions: 'CHOOSEPERMISSIONS',
            organizationSelect: 'ORGANIZATIONSELECT',
            teamSelect: 'TEAMSELECT',
            userSelect: 'USERSELECT',
            createNewACR: 'CREATENEWACR'
        };
        this.state = this.states.existing;
    }

    $onInit() {
        this.accessControlRules = [];
        this.actionTypes = [...this.defaultActions, ...this.resolve.extraActions];
        this.authTarget = {
            objectType: this.resolve.objectType,
            objectId: this.resolve.object.id
        };

        this.objectId = this.resolve.object.id;
        this.objectName = this.resolve.objectName;

        this.fetchPermissions();
        this.allRoles = this.authService.getUserRoles();

        this.organizationId = this.allRoles.filter(
            (role) => role.groupType === 'ORGANIZATION'
        )[0].groupId;
        this.platformId = this.allRoles.filter(
            (role) => role.groupType === 'PLATFORM'
        )[0].groupId;
    }

    countTeamMembers(team) {
        return this.teamService.getMembers(
            this.platformId, this.organizationId, team.id, 1, null
        ).then(
            (firstPage) => {
                this.affectedUsers = firstPage.count;
            }
        );
    }

    createNewACR() {
        switch (this.authTarget.objectType) {
        case 'scenes':
            this.permissionsService.create(
                this.authTarget,
                {
                    isActive: true,
                    subjectType: this.subjectType,
                    subjectId: this.selectedPermissionsTarget.id,
                    actionType: 'VIEW'
                }
            ).then(
                (accessControlRules) => {
                    this.setAccessControlRuleRows(accessControlRules);
                    this.setState('existing');
                }
            );
            break;
        default:
            break;
        }
        return;
    }

    fetchPermissions() {
        return this.permissionsService.query(this.authTarget).$promise.then(
            (permissions) => {
                this.setAccessControlRuleRows(permissions);
            }
        );
    }

    handleOrganizationSubjectSelection() {
        this.subjectType = 'ORGANIZATION';
        this.organizationService.getMembers(
            this.platformId, this.organizationId, 1, null
        ).then(
            (resp) => {
                this.affectedUsers = resp.count;
            }
        ).then(
            this.organizationService.getOrganization(this.organizationId).then(
                (organization) => {
                    this.selectedPermissionsTarget = organization;
                    this.setState('createNewACR');
                }
            )
        );
    }

    handleTeamSubjectSelection() {
        this.subjectType = 'TEAM';
        this.userService.getTeams().then(
            (teams) => {
                this.teams = teams;
                this.setState('teamSelect');
            }
        );
    }

    handleUserSubjectSelection() {
        this.subjectType = 'USER';
        this.setState('userSelect');
    }

    handleNewPermissionSubjectSelection() {
        switch (this.newPermissionSubject.target) {
        case 'EVERYONE':
            this.setState('choosePermissions');
            this.subjectType = 'ALL';
            break;
        case 'ORGANIZATION':
            this.handleOrganizationSubjectSelection();
            break;
        case 'TEAM':
            this.handleTeamSubjectSelection();
            break;
        default:
            this.handleUserSubjectSelection();
        }
    }

    searchUsers(value) {
        if (value && value.length >= 4) {
            this.platformService.getMembers(
                this.platformId, 0, value
            ).then(
                (resp) => {
                    this.availableUsers = _.uniqBy(resp.results, (user) => {
                        return user.email;
                    });
                }
            );
        } else {
            this.availableUsers = [];
        }
    }

    selectTeam(team) {
        this.selectedPermissionsTarget = team;
        this.countTeamMembers(this.selectedPermissionsTarget);
        this.setState('createNewACR');
    }

    selectUser(user) {
        this.selectedPermissionsTarget = Object.assign(user, {name: user.email});
        this.setState('createNewACR');
        this.availableUsers = [];
    }

    setAccessControlRuleRows(accessControlRules) {
        this.accessControlRuleRows = _.mapValues(
            _.groupBy(
                accessControlRules,
                (acr) => this.toTitle(acr.subjectType, acr.subjectId)
            ),
            (acrList) => _.map(acrList, (acr) => acr.actionType)
        );
    }

    setState(newState) {
        this.state = this.states[newState];
    }

    togglePermission(actionType, objectKey) {
        const existingActions = this.accessControlRuleRows[objectKey];
        const newRows = existingActions.includes(actionType) ?
              existingActions.filter(action => action !== actionType) :
              [...existingActions, actionType];
        this.accessControlRuleRows[objectKey] = newRows;
    }

    toTitle(subjectType, subjectId) {
        return subjectType === 'ALL' && subjectId === null ?
            'Everyone' :
            `${subjectType} ${subjectId}`;
    }

    updatePermissions() {
        let rules = [];
        for (let key in this.accessControlRuleRows) {
            if (this.accessControlRuleRows.hasOwnProperty(key)) {
                let transformed = _.map(
                    this.accessControlRuleRows[key],
                    (actionType) => {
                        return {
                            isActive: true,
                            subjectType: key.split(' ')[0],
                            subjectId: key.split(' ')[1],
                            actionType: actionType
                        };
                    }
                );
                rules = [...rules, ...transformed];
            }
        }
        this.permissionsService.update(
            this.authTarget,
            rules
        ).then(
            () => this.close()
        );
    }
}

const PermissionsModalModule = angular.module('components.permissions.permissionsModal', []);

PermissionsModalModule.component('permissionsModal', PermissionsModalComponent);
PermissionsModalModule.controller('PermissionsModalController', PermissionsModalController);

export default PermissionsModalModule;
