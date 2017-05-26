import React from "react";
import {Router, Route, IndexRoute, browserHistory} from "react-router";
import {Provider, connect} from "react-redux";

import store from "./store";
import actions from "./actions";

import rootConnector from "./connectors/root-connector";
import repositoriesConnector from "./connectors/repositories-connector";
import editRepositoryConnector from "./connectors/edit-repository-connector";
import repositoryStatusConnector from "./connectors/repository-status";

import App from "./components/app";

import Repositories from "./components/repositories/repositories";
import NewRepository from "./components/repositories/new";
import EditRepository from "./components/repositories/edit";
import RepositoryStatus from "./components/repositories/repository-status";


const urls = {
    root() {
        return "/";
    },
    newRepository() {
        return "/nieuw"
    },
    editRepository(id) {
        return id
            ? `/bewerken/${id}`
            : "/bewerken/:repositoryId"
    },
    repositoryStatus(id) {
        return id
            ? `/overzicht/${id}`
            : "/overzicht/:repositoryId"
    }
};

export { urls };

const navigateTo = (key, args) => browserHistory.push(urls[key].apply(null, args));

const connectComponent = (stateToProps) => connect(stateToProps, dispatch => actions(navigateTo, dispatch));

export default (
    <Provider store={store}>
        <Router history={browserHistory}>
            <Route path={urls.root()} component={connectComponent(rootConnector)(App)}>
                <IndexRoute component={connectComponent(repositoriesConnector)(Repositories) } />
                <Route path={urls.newRepository()} component={connectComponent(editRepositoryConnector)(NewRepository)} />
                <Route path={urls.editRepository()} components={connectComponent(editRepositoryConnector)(EditRepository)} />
                <Route path={urls.repositoryStatus()} components={connectComponent(repositoryStatusConnector)(RepositoryStatus)}/>
            </Route>
        </Router>
    </Provider>
);
