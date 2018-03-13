import angular from 'angular';

class PlatformUsersController {
    constructor(modalService) {
        this.modalService = modalService;
        this.fetchUsers();
    }

    fetchUsers() {
        this.users = [
            {
                id: '1',
                name: 'user one',
                email: 'user@example.com',
                organization: 'Organization one',
                role: 'Manager',
                teams: [1]
            },
            {
                id: '2',
                name: 'user two',
                email: 'user@example.com',
                organization: 'Organization two',
                role: 'Viewer',
                teams: []
            },
            {
                id: '3',
                name: 'user three',
                email: 'user@example.com',
                organization: 'Organization three',
                role: 'Uploader',
                teams: [1, 2, 3]
            },
            {
                id: '4',
                name: 'user four',
                email: 'user@example.com',
                organization: 'Organization four',
                role: 'Uploader',
                teams: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
            }
        ];

        this.users.forEach(
            (user) => Object.assign(
                user, {
                    options: {
                        items: this.itemsForUser(user)
                    }
                }
            ));
    }

    itemsForUser(user) {
        /* eslint-disable */
        return [
            {
                label: 'Edit',
                callback: () => {
                    console.log('edit callback for user:', user);
                }
            },
            {
                label: 'Delete',
                callback: () => {
                    console.log('delete callback for user:', user);
                },
                classes: ['color-danger']
            }
        ];
        /* eslint-enable */
    }

    newUserModal() {
        this.modalService.open({
            component: 'rfUserModal',
            resolve: { },
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            console.log('user modal closed with value:', result);
        });
    }
}

const PlatformUsersModule = angular.module('pages.platform.users', []);
PlatformUsersModule.controller('PlatformUsersController', PlatformUsersController);

export default PlatformUsersModule;
