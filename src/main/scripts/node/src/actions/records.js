import xhr from "xhr";
import {handleResponse} from "./response-handler";
import ActionTypes from "./action-types";

const findRecords = (query) => (dispatch) => {
    dispatch({type: ActionTypes.FIND_RESULT_PENDING});

    xhr({url: `/records/find?q=${encodeURIComponent(query)}`, "method": "GET", headers: {
        'Authorization': localStorage.getItem("authToken") }}, (err, resp, body) => handleResponse(resp, () => {
        dispatch({type: ActionTypes.RECEIVE_FIND_RESULT, data: JSON.parse(body)});
    }));
};

const fetchRecord = (ipName) => (dispatch) => {
    dispatch({type: ActionTypes.RECORD_PENDING});

    xhr({url: `/records/status/${ipName}?${new Date().getTime()}`, "method": "GET", headers: {
        'Authorization': localStorage.getItem("authToken") }}, (err, resp, body) => handleResponse(resp, () => {
        dispatch({type: ActionTypes.RECEIVE_RECORD, data: JSON.parse(body)});
    }));
};

const bulkResetToPending = (repositoryId) => (dispatch) => {
    xhr({url: `/records/bulk-reset/${repositoryId}`, "method": "PUT", headers: {
        'Authorization': localStorage.getItem("authToken") }}, (err, resp, body) => handleResponse(resp));
};

const resetRecord = (ipName) => (dispatch) =>{
    xhr({url: `/records/reset/${ipName}`, "method": "PUT", headers: {
        'Authorization': localStorage.getItem("authToken") }}, (err, resp, body) => handleResponse(resp, () =>
            dispatch(fetchRecord(ipName))
    ));
};

export {findRecords, fetchRecord, bulkResetToPending, resetRecord}