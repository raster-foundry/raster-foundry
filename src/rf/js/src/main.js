'use strict';

var React = require('react');

var HelloMessage = React.createClass({
    render: function() {
        return <div>Hello {this.props.name}</div>;
    }
});

React.render(<HelloMessage name="John" />, document.body);
