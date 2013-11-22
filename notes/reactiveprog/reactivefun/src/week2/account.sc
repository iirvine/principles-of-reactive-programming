package week2

object account {
  val acct = new BankAccount                      //> acct  : week2.BankAccount = week2.BankAccount@74b23210
  acct deposit 50
  acct withdraw 20                                //> res0: Int = 30
  acct withdraw 20                                //> res1: Int = 10
  acct withdraw 15                                //> java.lang.Error: insufficient funds
                                                  //| 	at week2.BankAccount.withdraw(BankAccount.scala:12)
                                                  //| 	at week2.account$$anonfun$main$1.apply$mcV$sp(week2.account.scala:8)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$$anonfun$$exe
                                                  //| cute$1.apply$mcV$sp(WorksheetSupport.scala:76)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$.redirected(W
                                                  //| orksheetSupport.scala:65)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$.$execute(Wor
                                                  //| ksheetSupport.scala:75)
                                                  //| 	at week2.account$.main(week2.account.scala:3)
                                                  //| 	at week2.account.main(week2.account.scala)
}