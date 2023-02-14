import { useEffect, useState } from 'react'
import { Button, Form, Grid, Label, Loader, Segment, TextArea } from 'semantic-ui-react'
import { getToken, post } from './../service/APIService';
import { useForm } from "react-hook-form";
import { ToastContainer, toast } from 'react-toastify';
import * as _ from 'lodash';
import { maskEmailAddress, replaceString } from '../utils/StringUtil';
import { useHistory } from 'react-router-dom/cjs/react-router-dom.min';
import { useSelector } from 'react-redux';

export const UpdateRegistry = ({ changeTab, formState, setState }) => {

    const { register, handleSubmit, watch, formState: { errors }, reset } = useForm();
    const [sending, setSending] = useState(false)
    const [loader, setLoader] = useState(false)
    const [token, setToken] = useState("")
    const [passwordVerified, setPasswordVerified] = useState(false)
    const [password, setPassword] = useState("")
    const [certType, setCertType] = useState("")
    let history = useHistory();
    const [formErrors, setFormErrors] = useState({});
    const [passwordType, setPasswordType] = useState("password")

    const togglePasswordView = () => {
        if (passwordType == 'password') {
            setPasswordType('text');
        } else {
            setPasswordType('password');
        }
    }

    const formStore = useSelector((state) => state)

    console.log(errors)

    useEffect(() => {
        if (_.get(formState, 'participant') == null) {
            setState({ ...formState, ...formStore.formState })
        }
    }, []);


    const onSubmit = (data) => {
        setLoader(true)
        setSending(true)
        const formData = { "jwt_token": token, participant: { "participant_code": _.get(formState, 'participant_code'), "participant_name": _.get(formState, 'participant.participant_name'), "endpoint_url": data.endpoint_url, "certificates_type": data.certificates_type, "encryption_cert": data.encryption_cert, "signing_cert_path": data.signing_cert_path } };
        post("/participant/onboard/update", formData).then((data => {
            toast.success("Form is submitted successfully", {
                position: toast.POSITION.TOP_CENTER, autoClose: 2000
            });
            reset()
            setTimeout(function () {
                history.push("/onboarding/end");
            }, 2000);
        })).catch(err => {
            toast.error(_.get(err, 'response.data.error.message') || "Internal Server Error", {
                position: toast.POSITION.TOP_CENTER
            });
        }).finally(() => {
            setSending(false)
            setLoader(false)
        })
    }

    const getAccessToken = () => {
        setSending(true)
        setLoader(true)
        setFormErrors({});
        let body = { "client_id": "registry-frontend", "username": _.get(formState, 'participant.primary_email'), "password": password, "grant_type": "password" }
        getToken("/auth/realms/swasth-health-claim-exchange/protocol/openid-connect/token", body)
            .then((data => {
                setToken(_.get(data, 'data.access_token'))
                setPasswordVerified(true);
            })).catch((err => {
                let errMsg = _.get(err, 'response.data.error_description')
                if (errMsg === 'Invalid user credentials') {
                    setFormErrors({ passwordIncorrect: 'Incorrect Password' });
                } else {
                    toast.error(errMsg || "Internal Server Error", {
                        position: toast.POSITION.TOP_CENTER
                    });
                }
            }))
            .finally(() => {
                setLoader(false)
                setSending(false)
            })
    }

    return <>
        <ToastContainer autoClose={false} />
        <Form onSubmit={handleSubmit(onSubmit)} className="container">
            {loader && <Loader active />}
            <div className='form-main' style={{ marginTop: '20px' }}>
                <Form.Field>
                    <b>Participant Code:</b>&ensp;{maskEmailAddress(_.get(formState, 'participant_code'))}
                </Form.Field>
                <Form.Field>
                    <b>Username:</b>&ensp;{maskEmailAddress(_.get(formState, 'participant.primary_email'))}
                </Form.Field>
                <Grid columns='equal' style={{ marginTop: '16px' }}>
                    <Form.Field className={{ 'error': 'password' in errors }} required>
                        <label>Password</label>
                    </Form.Field>
                    <Form.Field className={{ 'error': 'password' in errors }} required>
                        {passwordVerified ? (<div style={{ 'color': 'green', 'marginLeft': '0px' }}><b>verified</b></div>) : (<div style={{ 'color': 'red', 'marginLeft': '0px' }}><b>{formErrors.passwordIncorrect}</b></div>)}
                    </Form.Field>
                </Grid>
                <Form.Field disabled={passwordVerified} className={{ 'error': 'password' in errors }} required>
                    <div class="ui icon input"><input type={passwordType} placeholder="Enter Password" onInput={e => setPassword(e.target.value)} {...register("password", { required: true })} /><i aria-hidden="true" class="eye link icon" onClick={() => togglePasswordView()}></i></div>
                </Form.Field>
                {passwordVerified ? null :
                    <Button disabled={sending} onClick={getAccessToken} className="primary center-element button-color">
                        {sending ? "Verifying" : "Verify"}</Button>}
                {passwordVerified ?
                    <Form.Field disabled={sending} className={{ 'error': 'endpoint_url' in errors }} required>
                        <label>Endpoint URL</label>
                        <input className='input-text' placeholder='Enter Endpoint URL' {...register("endpoint_url", { required: true })} />
                    </Form.Field> : null}
                <Grid columns='equal' style={{ marginTop: '10px' }}>
                    {passwordVerified ?
                        <Form.Field disabled={sending} className={{ 'error': 'certificates_type' in errors }} required>
                            <label>Certificates Type</label>
                        </Form.Field> : null}
                    {passwordVerified ?
                        <Form.Field disabled={sending}>
                            <input
                                id="URL"
                                type="radio"
                                label='URL'
                                name='certificates_type'
                                value='URL'
                                onClick={e => setCertType('URL')}
                                {...register("certificates_type", { required: true })}
                            /> URL
                        </Form.Field> : null}
                    {passwordVerified ?
                        <Form.Field disabled={sending}>
                            <input
                                id="certificate_data"
                                type="radio"
                                label='Certificate Data'
                                name='certificates_type'
                                value='certificate_data'
                                onClick={e => setCertType('certificate_data')}
                                {...register("certificates_type", { required: true })}
                            /> Certificate Data
                        </Form.Field> : null}
                </Grid>
                {passwordVerified && certType ?
                    <Form.Field disabled={sending} required>
                        <label>Encryption Cert</label>
                        {certType === 'URL' ?
                            <input placeholder='Enter Encryption Cert Path' {...register("encryption_cert", { required: true })} />
                            : null}
                        {certType === 'certificate_data' ?
                            <textarea rows="5" placeholder='Enter Encryption Cert Data' {...register("encryption_cert", { required: true })}></textarea>
                            : null}
                    </Form.Field> : null}
                {passwordVerified && certType ?
                    <Form.Field disabled={sending} required>
                        <label>Signing Cert</label>
                        {certType === 'URL' ?
                            <input placeholder='Enter Signing Cert Path' {...register("signing_cert_path", { required: true })} />
                            : null}
                        {certType === 'certificate_data' ?
                            <textarea rows="5" placeholder='Enter Signing Cert Data' {...register("signing_cert_path", { required: true })}></textarea>
                            : null}
                    </Form.Field> : null}
            </div><br /><br />
            <Grid>
                <Grid.Row>
                    <Grid.Column>
                        {passwordVerified ?
                            <Button disabled={sending} type='submit' className="primary center-element button-color">
                                {sending ? "Submitting" : "Submit"}</Button> : null}
                    </Grid.Column>
                </Grid.Row>
            </Grid>
        </Form>
    </>

}