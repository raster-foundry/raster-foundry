export default class TokenItem {
    constructor() {
    }

    $onInit() {
        this.editing = false;
        this.newName = this.token.name;
    }

    onDelete() {
        this.onDelete({token: this.token});
    }

    startEditing() {
        this.editing = true;
    }

    onEditComplete(name) {
        this.editing = false;
        this.onUpdate({token: this.token, name: name});
    }

    onEditCancel() {
        this.newName = this.token.name;
        this.editing = false;
    }
}
