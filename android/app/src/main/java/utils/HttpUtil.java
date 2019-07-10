package utils;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.android.ConfirmActivity;
import com.example.android.LoginActivity;
import com.example.android.RegisterActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Date;
import java.util.Map;

import static android.content.ContentValues.TAG;

public class HttpUtil {

    private static HttpUtil mHttpUtil = new HttpUtil();
    private static String mBaseAddress = "http://47.95.214.69:1002";
    private static String mServerPublicKey = "";

    private String mUserId = "";
    private String mPhoneId = "";
    private String mUserPrivateKey = "";
    private String mToken = "";

    public static HttpUtil getInstance() {
        return mHttpUtil;
    }

    public static void initServerPublicKey() {
        RequestForKey requestForKey = new RequestForKey();
        Log.v(TAG, "准备向服务器获取公钥");
        requestForKey.execute(mBaseAddress + "/app/key");
    }

    public static String getServerPublicKey() {
        if (mServerPublicKey.isEmpty())
            initServerPublicKey();
        return mServerPublicKey;
    }

    public String getUserId() {
        return mUserId;
    }

    public String getUserPrivateKey() {
        return mUserPrivateKey;
    }

    public void register(String userName, String cardId, String pinCode, Handler handler) {
        String serverPublicKey = getServerPublicKey();
        RequestToRegister requestToRegister = new RequestToRegister(handler);
        JSONArray requests = new JSONArray();
        try {
            JSONObject address = new JSONObject();
            address.put("address", mBaseAddress + "/app/register");

            Map<String, String> userKeyPair = SecurityUtil.getRSAKeyPair();
            String userPublicKey = userKeyPair.get("public_key");
            String userPrivateKey = userKeyPair.get("private_key");

            Log.v(TAG, "用户生成的公钥是：" + userPublicKey);
            Log.v(TAG, "用户生成的私钥是：" + userPrivateKey);

            String phoneId = "Xiaomi 9 SE";

            String key = SecurityUtil.getDESKeyString();
            Log.v(TAG, "随机生成的对称密钥是：" + key);
            String encryptedKey = SecurityUtil.encryptStringByRSAPublicKeyString(key, serverPublicKey);
            Log.v(TAG, "已成功使用公钥加密对称钥");

            Date date = new Date();
            String time = String.valueOf(date.getTime());
            String signedHash = SecurityUtil.signStringByRSAPrivateKeyString(userName + cardId + phoneId + userPublicKey + time, userPrivateKey);

            JSONObject data = new JSONObject();

            data.put("user_name", userName);
            data.put("card_id", cardId);
            data.put("phone_id", phoneId);
            data.put("public_key", userPublicKey);
            data.put("time", time);
            data.put("signed_hash", signedHash);
            String desData = SecurityUtil.encryptStringByDESKeyString(data.toString(), key);

            JSONObject body = new JSONObject();
            body.put("encrypted_key", encryptedKey);
            body.put("data", desData);

            JSONObject info = new JSONObject();
            info.put("user_name", userName);
            info.put("private_key", userPrivateKey);
            info.put("pin_code", pinCode);

            requests.put(address);
            requests.put(body);
            requests.put(info);

            Log.v(TAG, "正准备向后台发送注册请求包");
        } catch (JSONException jsonException) {
            jsonException.printStackTrace();
        }
        requestToRegister.execute(requests);
    }

    public void pinLogin(String userId, String pinCode, Handler handler) {
        String serverPublicKey = getServerPublicKey();
        RequestToPinLogin requestToPinLogin = new RequestToPinLogin(handler);
        JSONArray requests = new JSONArray();
        try {
            JSONObject address = new JSONObject();
            address.put("address", mBaseAddress + "/app/normal_login");

            // 用PIN码从用户私钥文件提取出私钥字符串，每次需要更新当前操作的用户的信息时，都要运行这两句
            getInstance().setUserId(userId);
            getInstance().initUserPrivateKey(pinCode);

            String phoneId = "Xiaomi 9 SE";

            String key = SecurityUtil.getDESKeyString();
            String encryptedKey = SecurityUtil.encryptStringByRSAPublicKeyString(key, serverPublicKey);
            Log.v(TAG, "已成功使用公钥加密对称钥");
            String signedHash = SecurityUtil.signStringByRSAPrivateKeyString(userId + phoneId, mUserPrivateKey);

            JSONObject data = new JSONObject();
            data.put("user_id", userId);
            data.put("phone_id",phoneId);
            data.put("signed_hash", signedHash);
            String desData = SecurityUtil.encryptStringByDESKeyString(data.toString(), key);

            JSONObject body = new JSONObject();
            body.put("encrypted_key", encryptedKey);
            body.put("data", desData);

            requests.put(address);
            requests.put(body);

            Log.v(TAG, "正准备向后台发送登录请求包：" + body.toString());
        } catch (JSONException jsonException) {
            jsonException.printStackTrace();
        }
        requestToPinLogin.execute(requests);

    }

    public void getQrCode(Handler handler) {
        RequestForQrcode requestForQrcode = new RequestForQrcode(handler);
        requestForQrcode.execute(mBaseAddress + "/hi/auth_code");
    }

//    public void qrLogin(String userId, String pinCode, Handler handler) {
//
//    }

    public void confirm(String qrCode,Handler handler) {
        String serverPublicKey = getServerPublicKey();
        String userPrivateKey = "";
        RequestToConfirm requestToConfirm = new RequestToConfirm(handler);
        JSONArray requests = new JSONArray();
        try {
            String key = SecurityUtil.getDESKeyString();
            String encryptedKey = SecurityUtil.encryptStringByRSAPublicKeyString(key, serverPublicKey);

            JSONObject data= new JSONObject();
            Date date = new Date();
            String time = String.valueOf(date.getTime());

            String signedHash = SecurityUtil.signStringByRSAPrivateKeyString(time + mUserId  + mPhoneId + qrCode , userPrivateKey);

            data.put("time", time);
            data.put("user_id", mUserId);
            data.put("phone_id", mPhoneId);
            data.put("random_str", qrCode);
            data.put("signed_hash", signedHash);

            String desData = SecurityUtil.encryptStringByDESKeyString(data.toString(), key);

            JSONObject body = new JSONObject();
            body.put("encrypted_key",encryptedKey);
            body.put("data",desData);

            JSONObject addr = new JSONObject();
            addr.put("address",mBaseAddress + "/app/help_login");

            requests.put(addr);
            requests.put(body);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        requestToConfirm.execute(requests);
    }

    private static String getStringFromInputStream(InputStream inputStream) {
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        StringBuilder stringBuilder = new StringBuilder();
        try {
            String temp;
            while ((temp = bufferedReader.readLine()) != null)
                stringBuilder.append(temp);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    private static JSONObject getJSONObjectFromInputStream(InputStream inputStream) {
        String string = getStringFromInputStream(inputStream);
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(string);
        } catch (JSONException jsonException) {
            jsonException.printStackTrace();
            jsonObject = null;
        }
        return jsonObject;
    }

    private void setUserId(String userId) {
        mUserId = userId;
        Log.v(TAG, "更新当前操作用户的ID为：" + userId);
    }

    private void setToken(String token) {
        mToken = token;
    }

    private void setUserPrivateKey(String userPrivateKey) {
        mUserPrivateKey = userPrivateKey;
        Log.v(TAG, "更新当前操作用户的私钥为：" + mUserPrivateKey);
    }

    private void initUserPrivateKey(String pinCode) {
        String desKeyString = SecurityUtil.getDESKeyString(pinCode);
        String ekey = FileUtil.readFile(mUserId);
        setUserPrivateKey(SecurityUtil.decryptStringByDESKeyString(ekey, desKeyString));
    }

    private static class RequestForKey extends AsyncTask<String, Integer, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... strings) {
            try {
                URL url = new URL(strings[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setReadTimeout(5000);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    return getJSONObjectFromInputStream(inputStream).get("public_key_of_server").toString();
                } else
                    return "GET the URL successfully but the public_key_of_server is wrong!";
            } catch (MalformedURLException malformedURLException) {
                malformedURLException.printStackTrace();
                return "URL is wrong!";
            } catch (ProtocolException protocolException) {
                protocolException.printStackTrace();
                return "Property setting is wrong!";
            } catch (IOException ioException) {
                ioException.printStackTrace();
                return "Input is wrong!";
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
                return "Public key of server is wrong!";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            HttpUtil.mServerPublicKey = result;
            Log.v(TAG, "服务器公钥是：" + HttpUtil.mServerPublicKey);
        }
    }

    private static class RequestToRegister extends AsyncTask<JSONArray, Integer, JSONObject> {

        private Handler mHandler;

        public RequestToRegister(Handler handler) {
            mHandler = handler;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected JSONObject doInBackground(JSONArray... requests) {
            try {
                JSONObject address = requests[0].getJSONObject(0);
                JSONObject body = requests[0].getJSONObject(1);
                JSONObject info = requests[0].getJSONObject(2);

                String userName = (String) info.get("user_name");
                String userPrivateKey = (String) info.get("private_key");
                String pinCode = (String) info.get("pin_code");

                URL url = new URL((String) address.get("address"));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setReadTimeout(5000);
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("Content-type", "application/x-java-serialized-object");

                PrintStream printStream = new PrintStream(connection.getOutputStream());
                printStream.print(body.toString());
                printStream.flush();
                printStream.close();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    Log.v(TAG, "响应码为200");
                    InputStream inputStream = connection.getInputStream();
                    Log.v(TAG, "已获取响应的输入流");
                    Message message = mHandler.obtainMessage();
                    JSONObject response = getJSONObjectFromInputStream(inputStream);
                    String encryptedKey = (String) response.get("encrypted_key");
                    Log.v(TAG,"加密过得des密文:"+encryptedKey);
                    String key = SecurityUtil.decryptStringByRSAPrivateKeyString(encryptedKey, userPrivateKey);

                    Log.v(TAG, encryptedKey);
                    Log.v(TAG, key);

                    int statusCode = (int) response.get("status_code");

                    JSONObject mesObj = new JSONObject();
                    switch (statusCode) {
                        case 200:
                            message.what = RegisterActivity.MESSAGE_REGISTER_SUCCESS_RESPONSE;

                            String desData = (String) response.get("data");
                            JSONObject data = new JSONObject(SecurityUtil.decryptStringByDESKeyString(desData, key));
                            String userId = (String) data.get("user_id");
                            String signedHash = (String) data.get("signed_hash");

                            if (SecurityUtil.verifyStringByRSAPublicKeyString(userId, signedHash, HttpUtil.getServerPublicKey())) {
                                Log.v(TAG, "服务器的响应验签通过");
                                mesObj.put("user_name", userName);
                                mesObj.put("user_id", userId);
                                mesObj.put("error", null);
                                Log.v(TAG, "获取到的用户ID是：" + userId);

                                Log.v(TAG, "正要将这个私钥写入文件:" + userPrivateKey);

                                // 用PIN码将私钥字符串加密然后写入文件
                                String desKeyString = SecurityUtil.getDESKeyString(pinCode);
                                String ekey = SecurityUtil.encryptStringByDESKeyString(userPrivateKey, desKeyString);
                                FileUtil.writeFile(userId, ekey);
                            } else {
                                mesObj.put("error", "Got malicious message!");
                            }
                            message.obj = mesObj;
                            break;
                        case 400:
                            message.what = RegisterActivity.MESSAGE_REGISTER_FAIL_RESPONSE;

                            mesObj.put("error", "Server doesn't distribute any ID!");
                            message.obj = mesObj;
                            break;
                        default:
                            break;
                    }
                    message.sendToTarget();
                }
                else {
                    System.out.println("POST to register failed!");
                }
            } catch (MalformedURLException malformedUrlException) {
                malformedUrlException.printStackTrace();
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            } catch (IOException ioException) {
               ioException.printStackTrace();
            }

            // 直接返回一个空JSONObject，如果需要结束后做什么事，可以在这里扩展
            JSONObject result = new JSONObject();
            return result;
        }

        @Override
        protected void onPostExecute(JSONObject response) {
            super.onPostExecute(response);
            Log.v(TAG, "申请注册已结束");
        }
    }

    private static class RequestToPinLogin extends AsyncTask<JSONArray, Integer, JSONObject> {

        private Handler mhandler;

        public RequestToPinLogin(Handler handler) {
            mhandler = handler;
        }

        @Override
        protected  void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected JSONObject doInBackground(JSONArray... requests) {
            try {
                JSONObject address = requests[0].getJSONObject(0);
                JSONObject body = requests[0].getJSONObject(1);

                URL url = new URL((String) address.get("address"));

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setReadTimeout(5000);
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("Content-type", "application/x-java-serialized-object");

                PrintStream printStream = new PrintStream(connection.getOutputStream());
                printStream.print(body.toString());
                printStream.flush();
                printStream.close();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    Log.v(TAG, "响应码为200");
                    InputStream inputStream = connection.getInputStream();
                    Log.v(TAG, "已获取响应的输入流");
                    Message message = mhandler.obtainMessage();
                    JSONObject response = getJSONObjectFromInputStream(inputStream);
                    String encryptedKey = (String) response.get("encrypted_key");

                    Log.v(TAG, "登录前拿到的私钥是：" + HttpUtil.getInstance().getUserPrivateKey());
                    Log.v(TAG, "加密过得des的密文是：" + encryptedKey);

                    String key = SecurityUtil.decryptStringByRSAPrivateKeyString(encryptedKey, HttpUtil.getInstance().getUserPrivateKey());

                    Log.v(TAG, encryptedKey);
                    Log.v(TAG, key);

                    int statusCode = (int) response.get("status_code");
                    JSONObject mesObj = new JSONObject();
                    switch (statusCode) {
                        case 200:
                            message.what = LoginActivity.MESSAGE_PINLOGIN_SUCCESS_RESPONSE;

                            String desData = (String) response.get("data");
                            JSONObject data = new JSONObject(SecurityUtil.decryptStringByDESKeyString(desData, key));
                            String token = (String) data.get("token");
                            String signedHash = (String) data.get("signed_hash");

                            if (SecurityUtil.verifyStringByRSAPublicKeyString(token, signedHash, HttpUtil.getServerPublicKey())) {
                                Log.v(TAG, "服务器的响应验签通过");
                                mesObj.put("token", token);
                                mesObj.put("error", null);

                                HttpUtil.getInstance().setToken(token);
                                Log.v(TAG, "获取到的token：" + token);
                            } else {
                                mesObj.put("error", "Got malicious message!");
                            }
                            message.obj = mesObj;
                            break;
                        case 400:
                            message.what = LoginActivity.MESSAGE_PINLOGIN_FAIL_RESPONSE;
                            mesObj.put("error", "PIN Login failed!");
                            message.obj = mesObj;

                            Log.v(TAG, "用户未通过身份验证");
                            break;
                        default:
                            break;
                    }
                    message.sendToTarget();
                }
                else {
                    System.out.println("POST to register failed!");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 直接返回一个空JSONObject，如果需要结束后做什么事，可以在这里扩展
            JSONObject result = new JSONObject();
            return result;
        }

        @Override
        protected void onPostExecute(JSONObject response) {
            super.onPostExecute(response);
            Log.v(TAG, "申请PIN码登录已结束");
        }
    }

    private static class RequestForQrcode extends AsyncTask<String, Integer, String> {

        private Handler mHander;

        public RequestForQrcode(Handler handler) {
            this.mHander = handler;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... strings) {
            String qrCode = "";
            try {
                URL url = new URL(strings[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setReadTimeout(5000);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();

                    JSONObject data = new JSONObject(getJSONObjectFromInputStream(inputStream).get("data").toString());
                    qrCode = (String) data.get("str");

                    Message message = mHander.obtainMessage();
                    message.what = LoginActivity.GET_QR_CODE_SUCCESS;
                    message.obj = qrCode;
                }
                else
                    return "GET the URL successfully but the qr_code is wrong!";
            } catch (MalformedURLException malformedURLException) {
                malformedURLException.printStackTrace();
                return "URL is wrong!";
            } catch (ProtocolException protocolException) {
                protocolException.printStackTrace();
                return "Property setting is wrong!";
            } catch (IOException ioException) {
                ioException.printStackTrace();
                return "Input is wrong!";
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
                return "Public qr_code of server is wrong!";
            }
            return qrCode;
        }
    }

    private class RequestToConfirm extends AsyncTask<JSONArray, Integer, String> {

        private Handler mHandler;

        public RequestToConfirm(Handler handler) {
            this.mHandler= handler;
        }

        @Override
        protected String doInBackground(JSONArray... jsonArrays) {
            try {
                JSONObject addr = jsonArrays[0].getJSONObject(0);
                JSONObject body = jsonArrays[0].getJSONObject(1);
                String address = (String) addr.get("address");

                URL url = new URL(address);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setReadTimeout(5000);
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("Content-type", "application/x-java-serialized-object");

                PrintStream printStream = new PrintStream(connection.getOutputStream());
                printStream.print(body.toString());
                printStream.flush();
                printStream.close();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    Log.v(TAG, "响应码为200");
                    InputStream inputStream = connection.getInputStream();
                    Message message = mHandler.obtainMessage();
                    Log.v(TAG, "已获取响应的输入流");
                    JSONObject response = getJSONObjectFromInputStream(inputStream);

                    int statusCode = (int) response.get("status_code");

                    JSONObject mesObj = new JSONObject();
                    switch (statusCode) {
                        case 200:
                            message.what = ConfirmActivity.CONFIRM_SUCCESS;
                            break;
                        case 400:
                            message.what = ConfirmActivity.CONFIRM_FAIL;
                            break;
                        default:
                            break;
                    }
                    message.sendToTarget();
                }
                else {
                    System.out.println("POST to confirm failed!");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 直接返回一个空字符串，如果需要结束后做什么事，可以在这里扩展
            return "";
        }
    }
}
