'use strict';

var React = require('react'),
    asset = require('../../core/utils').asset;

var Library = React.createBackboneClass({
    render: function() {
        return <div>
            <p>Logged in!</p>
            <p><a href="#" onClick={this.props.handleLogout}>Logout</a></p>
        </div>;
    }
});

module.exports = Library;
