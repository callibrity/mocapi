/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.session.dynamodb;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mocapi.session.dynamodb")
public class DynamoDbSessionStoreProperties {

  private String tableName = "mocapi_sessions";
  private boolean verifyTable = true;
  private boolean ensureTtl = true;

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public boolean isVerifyTable() {
    return verifyTable;
  }

  public void setVerifyTable(boolean verifyTable) {
    this.verifyTable = verifyTable;
  }

  public boolean isEnsureTtl() {
    return ensureTtl;
  }

  public void setEnsureTtl(boolean ensureTtl) {
    this.ensureTtl = ensureTtl;
  }
}
