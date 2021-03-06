import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { RedirectPageComponent } from './redirect-page.component';
import { RedirectCardComponent } from '../redirect-card/redirect-card.component';

describe('RedirectPageComponent', () => {
  let component: RedirectPageComponent;
  let fixture: ComponentFixture<RedirectPageComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      declarations: [RedirectPageComponent, RedirectCardComponent]
    })
      .overrideComponent(RedirectPageComponent, {
        set: {
          providers: [
            {
              provide: ActivatedRoute,
              useValue: {
                params: of({ location: 'adorsys.de' }),
                paramMap: {
                  subscribe(location: string): string {
                    return 'adorsys.de';
                  }
                }
              }
            }
          ]
        }
      })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(RedirectPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
