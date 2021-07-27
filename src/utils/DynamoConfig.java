package utils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import java.util.HashMap;
import java.util.Map;

public class DynamoConfig {

    private AmazonDynamoDB dynamoDB;

    public DynamoConfig() {
        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials_ricky file located at
         * (~/.aws/credentials_ricky).
         */
        AppConfig config = new AppConfig("appconfig.json");
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider(".aws/" + config.cluster.credentialsFile, "default");
        //ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        try {
            credentialsProvider.getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials_ricky from the credential profiles file. " +
                            "Please make sure that your credentials_ricky file is at the correct " +
                            "location (~/.aws/credentials_ricky), and is in valid format.",
                    e);
        }
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
    }

    public void insert(String tableName, Map<String, AttributeValue> item) {
        try {
            //String tableName = "instrumented-data";
            PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
            PutItemResult putItemResult = dynamoDB.putItem(putItemRequest);
            //System.out.println("Result: " + putItemResult);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }

    public ScanResult read(String tableName) {
        ScanResult scanResult = null;
        try {
            //String tableName = "instrumented-data";
            ScanRequest scanRequest = new ScanRequest(tableName);
            scanResult = dynamoDB.scan(scanRequest);
            //System.out.println("ScanResult:"+scanResult);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
        return scanResult;
    }

    public ScanResult read(String tableName, DeserializedParameters params) {
        ScanResult scanResult = null;
        try {
            Condition condition1 = new Condition().withAttributeValueList(new AttributeValue()
                    .withS(params.mapType)).withComparisonOperator(ComparisonOperator.EQ.toString());
            Condition condition2 = new Condition().withAttributeValueList(new AttributeValue()
                    .withS(params.strategy)).withComparisonOperator(ComparisonOperator.EQ.toString());
            HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
            scanFilter.put("mapType", condition1);
            scanFilter.put("strategy", condition2);
            //scanFilter.put("year", condition_);
            //String tableName = "instrumented-data";
            ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
            scanResult = dynamoDB.scan(scanRequest);
            //System.out.println("ScanResult:"+scanResult);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
        return scanResult;
    }

    public ScanResult read(String tableName, String conditionType, String condition, String attribute) {
        ScanResult scanResult = null;
        try {
            Condition condition_ = new Condition().withAttributeValueList(new AttributeValue().withN(condition));
            ;
            switch (conditionType) {
                case "equals":
                    condition_.withComparisonOperator(ComparisonOperator.EQ.toString());
                    break;
                case "greater":
                    condition_.withComparisonOperator(ComparisonOperator.GT.toString());
                    break;
                default:
                    break;
            }
            HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
            scanFilter.put(attribute, condition_);
            //scanFilter.put("year", condition_);
            //String tableName = "instrumented-data";
            ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
            scanResult = dynamoDB.scan(scanRequest);
            //System.out.println("ScanResult:"+scanResult);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
        return scanResult;
    }

    public void createTable(String tableName) {
        try {
            // Create a table with a primary hash key named 'name', which holds a string
            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                    .withKeySchema(new KeySchemaElement().withAttributeName("name").withKeyType(KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition().withAttributeName("name").withAttributeType(ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

            // Create table if it does not exist yet
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, tableName);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        } catch (InterruptedException ace) {
            System.out.println("Interrupted Exception.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }

    public String describeTable(String tableName) {
        TableDescription tableDescription = null;
        try {
            DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
            tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
            System.out.println("Table Description: " + tableDescription);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
        return tableDescription.toString();
    }

    public AmazonDynamoDB getDynamoDB() {
        return dynamoDB;
    }
}
