/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.python.checks.cdk;

import java.util.List;
import org.sonar.check.Rule;

import static org.sonar.python.checks.cdk.CdkPredicate.isFqn;
import static org.sonar.python.checks.cdk.CdkPredicate.isString;
import static org.sonar.python.checks.cdk.CdkUtils.getArgument;

@Rule(key = "S6333")
public class PublicApiIsSecuritySensitiveCheck extends AbstractCdkResourceCheck {
  private static final String MESSAGE = "Make sure that creating public APIs is safe here.";
  private static final String OMITTING_MESSAGE = "Omitting \"authorization_type\" disables authentication. Make sure it is safe here.";

  @Override
  protected void registerFqnConsumer() {
    checkFqns(List.of("aws_cdk.aws_apigateway.CfnMethod", "aws_cdk.aws_apigatewayv2.CfnRoute"), (subscriptionContext, callExpression) ->
      getArgument(subscriptionContext, callExpression, "authorization_type").ifPresentOrElse(
        argument -> argument.addIssueIf(isString("NONE"), MESSAGE),
        () -> subscriptionContext.addIssue(callExpression.callee(), OMITTING_MESSAGE)
      )
    );
    checkFqn("aws_cdk.aws_apigateway.Resource.add_method", (subscriptionContext, callExpression) ->
      getArgument(subscriptionContext, callExpression, "authorization_type").ifPresent(
        argument -> argument.addIssueIf(isFqn("aws_cdk.aws_apigateway.AuthorizationType.NONE"), MESSAGE)
      )
    );
  }
}