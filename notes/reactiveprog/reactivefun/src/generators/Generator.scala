package generators

trait Generator[+T] {
    self =>
    def generate: T

    def map[S](f: T => S): Generator[S] = new Generator[S] {
        def generate = f(self.generate) 
    }
    
    def flatMap[S](f: T => Generator[S]): Generator[S] = new Generator[S] {
      def generate = f(self.generate).generate
    }
}